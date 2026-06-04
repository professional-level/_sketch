# Laor Flink and Temporal Integration Plan

## Purpose

이 문서는 Laor 전략 실행 흐름에 Apache Flink를 붙일 때의 권장 아키텍처와 구현 계획을 정리한다.

핵심 목표는 Flink를 기술 과시용으로 무리하게 넣는 것이 아니라, 실제로 복잡해지는 주문 이벤트 흐름을 안전하게 정리하는 것이다. 특히 다음 흐름을 명확히 다룬다.

```text
주문 intent 생성
  -> 주문 제출됨
  -> 부분 체결 이벤트 누적
  -> 완전 체결 또는 거절/취소/타임아웃 판단
  -> Laor 전략의 다음 단계 진행
```

## Decision Summary

Flink는 Laor 전략의 중심 오케스트레이터가 아니다. Temporal Workflow도 제거하지 않는다.

권장 경계는 다음과 같다.

```text
stock-purchase-service
  -> raw order lifecycle events
  -> Kafka
  -> Flink milestone detector
  -> Laor milestone events
  -> strategy-execution-service
  -> Temporal Workflow signal
  -> next order intent
```

즉 Flink는 raw 주문 이벤트를 전략 진행에 의미 있는 milestone 이벤트로 정제한다. Temporal은 여전히 Laor 전략 인스턴스의 장기 실행 workflow와 다음 command 결정을 담당한다.

## Responsibility Boundaries

### stock-purchase-service

책임:

- broker order submit
- broker order id 저장
- broker reconciliation 또는 체결 조회
- `OrderSubmitted`, `OrderPartiallyFilled`, `OrderFilled`, `OrderRejected`, `OrderCancelled` 발행
- 주문 제출 및 체결 이벤트 발행 outbox 보장

금지:

- Laor 전략 단계 판단
- 다음 전략 주문 결정

### Flink Job

책임:

- 주문 이벤트를 `strategyExecutionId`와 `orderIntentId` 기준으로 group
- partial fill 수량/평균가 누적
- 주문 lifecycle 상태 계산
- full fill, reject, cancel, timeout, anomaly 감지
- Laor milestone 이벤트 발행

금지:

- broker API 직접 호출
- strategy source-of-truth DB 직접 변경
- order intent를 최종 확정 저장
- Temporal Workflow를 대체하는 장기 실행 business process 소유

### strategy-execution-service

책임:

- Laor strategy execution source of truth
- Temporal Workflow 시작/조회/signal 처리
- Flink milestone event 소비
- Workflow signal 전송
- Workflow 결정에 따른 다음 order intent outbox 저장

### Temporal Workflow

책임:

- Laor 전략 인스턴스의 장기 실행 흐름
- 매수 완료 대기, 매도 주문 생성, 매도 완료 대기
- timeout, retry, pause/resume, manual intervention
- 보상 또는 운영 결정 지점 표현

Flink 도입 후 Temporal이 덜 해도 되는 일:

- raw partial fill 이벤트를 하나씩 해석
- 주문 이벤트 순서 검증
- 주문별 체결 누적 projection
- timeout/anomaly detection의 저수준 이벤트 처리

Temporal이 계속 해야 하는 일:

- 다음 주문을 만들지 말지 결정
- 전략 상태 저장
- activity retry/timeout 관리
- 수동 중지/재개/보상 흐름 관리

## Target Event Flow

```text
strategy-execution-service
  -> OrderIntentCreated

stock-purchase-service
  -> OrderSubmitted
  -> OrderPartiallyFilled
  -> OrderFilled
  -> OrderRejected
  -> OrderCancelled

stream-processing-service
  -> LaorOrderSubmitted
  -> LaorEntryBuyPartiallyFilled
  -> LaorEntryBuyFilled
  -> LaorExitSellPartiallyFilled
  -> LaorExitSellFilled
  -> LaorOrderRejected
  -> LaorOrderCancelled
  -> LaorOrderFillTimeoutDetected
  -> LaorOrderAnomalyDetected

strategy-execution-service
  -> consume Laor milestone event
  -> signal Temporal workflow
  -> create next order intent when workflow decides
```

## Proposed Module Layout

Flink는 각 Spring Boot service 내부에 embed하지 않고 별도 모듈과 별도 배포 단위로 둔다.
모듈은 특정 전략명이나 Flink 기술명이 아니라 stream processing 책임을 드러내는 `stream-processing-service`로 두고, LAOR 처리는 그 내부의 `laor` job/package로 격리한다.

```text
strategy-execution-service
  src/main/kotlin/...

stream-processing-service
  src/main/kotlin/com/example/streamprocessingservice/laor
    application/
      OrderLifecycleJob.kt
      OrderLifecycleState.kt
      OrderLifecycleProcessor.kt
    adapter/in/kafka/
      OrderExecutionEventDeserializer.kt
    adapter/out/kafka/
      OrderLifecycleEventSerializer.kt
```

운영 배포 단위:

```text
strategy-execution-service Deployment
stock-purchase-service Deployment
stream-processing-service FlinkDeployment
```

## Kafka Topic Plan

Input topics:

- `order-intent-created`
- `order-submitted`
- `order-partially-filled`
- `order-filled`
- `order-rejected`
- `order-cancelled`

Output topics:

- `laor-order-milestone-detected`
- `laor-order-anomaly-detected`

Optional projection topic:

- `laor-order-lifecycle-projection-updated`

Keying rule:

| Topic | Kafka key |
| --- | --- |
| raw order execution topics | `strategyExecutionId` or `orderIntentId` |
| Laor milestone topics | `strategyExecutionId` |
| anomaly topics | `strategyExecutionId` |

권장 처리 단위는 `strategyExecutionId` 기준이다. 단, 같은 전략 안에 여러 주문이 동시에 존재할 수 있다면 Flink 내부 state는 `orderIntentId`별로 나눈다.

## Flink State Model

Flink job은 `strategyExecutionId`로 keyBy 후, 주문별 state를 보관한다.

```text
ValueState<OrderLifecycleState>
MapState<orderIntentId, OrderLifecycleState>
MapState<eventId, ProcessedEventMarker>
```

`OrderLifecycleState` 후보:

```text
orderIntentId
strategyExecutionId
orderTag
side
expectedQuantity
submitted
submittedAt
brokerOrderId
filledQuantity
averageFilledPrice
lastEventAt
terminalStatus
terminalAt
```

중복 방어:

- `eventId` 기준으로 deduplication한다.
- `eventId`가 없는 이벤트는 `topic + partition + offset`이 아니라 도메인 필드 기반 deterministic id를 만든다.
- dedup state에는 TTL을 둔다.

타이머:

- 주문 제출 후 설정 시간 안에 fill/reject/cancel이 없으면 timeout 후보를 emit한다.
- event-time 기반을 우선하고, 운영 편의상 processing-time fallback을 둘 수 있다.

## Milestone Event Contract

`LaorOrderMilestoneDetected` 후보 필드:

```text
event_id
milestone_type
strategy_execution_id
order_intent_id
broker_order_id
order_tag
side
filled_quantity
average_filled_price
occurred_at
source_event_ids
idempotency_key
```

`milestone_type` 후보:

```text
ENTRY_BUY_SUBMITTED
ENTRY_BUY_PARTIALLY_FILLED
ENTRY_BUY_FILLED
EXIT_SELL_SUBMITTED
EXIT_SELL_PARTIALLY_FILLED
EXIT_SELL_FILLED
ORDER_REJECTED
ORDER_CANCELLED
ORDER_FILL_TIMEOUT_DETECTED
```

`LaorOrderAnomalyDetected` 후보:

```text
FILL_BEFORE_SUBMIT
DUPLICATE_TERMINAL_EVENT
FILLED_QUANTITY_EXCEEDS_EXPECTED
CONFLICTING_BROKER_ORDER_ID
UNKNOWN_ORDER_INTENT
LATE_EVENT_AFTER_TERMINAL
```

## Temporal Integration

`strategy-execution-service`는 Flink output topic을 소비하고, milestone을 Temporal Workflow signal로 변환한다.

```text
Kafka LaorOrderMilestoneDetected
  -> StrategyExecutionEventListenerAdapter
  -> SignalLaorWorkflowUseCase
  -> Temporal Workflow signal
```

Workflow signal 후보:

```text
onEntryBuySubmitted
onEntryBuyPartiallyFilled
onEntryBuyFilled
onExitSellSubmitted
onExitSellPartiallyFilled
onExitSellFilled
onOrderRejected
onOrderCancelled
onOrderTimeoutDetected
```

Workflow는 signal을 받은 뒤 다음 command를 결정한다.

```text
entry-buy-filled
  -> calculate next Laor state
  -> create exit sell order intent

exit-sell-filled
  -> calculate realized result
  -> complete or schedule next cycle
```

## Implementation Phases

### Phase 0. Reliability Baseline

Flink 도입 전 선행 조건:

- consumer idempotency가 `SUCCESS`, `FAILED`, `PROCESSING`을 구분해야 한다.
- `FAILED` 또는 lease 만료 `PROCESSING`은 재처리 가능해야 한다.
- listener error handler와 DLT를 추가해야 한다.
- `strategy-execution-service` Kafka profile 설정을 명시해야 한다.

Flink가 이 문제를 대신 해결하지 않는다.

### Phase 1. Event Contract Stabilization

작업:

- raw order execution proto에 `event_id`, `strategy_execution_id`, `order_intent_id`, `order_tag`, `side`, `quantity`, `occurred_at`을 확정한다.
- `OrderSubmitted`, `OrderFilled`, `OrderPartiallyFilled`, `OrderRejected`, `OrderCancelled`의 key 규칙을 확정한다.
- Laor milestone proto를 추가한다.

완료 기준:

- raw event만으로 주문 lifecycle을 재구성할 수 있다.
- milestone event idempotency key를 deterministic하게 만들 수 있다.

### Phase 2. Flink Job Skeleton

작업:

- `stream-processing-service` 모듈 추가
- Kafka source/sink 설정
- protobuf deserialize/serialize 추가
- 단순 pass-through 또는 submitted milestone emit 구현

완료 기준:

- local Kafka에서 `OrderSubmitted`를 넣으면 `LaorOrderSubmitted`가 output topic에 나온다.
- Flink checkpoint directory가 설정되어 있다.

### Phase 3. Stateful Lifecycle Aggregation

작업:

- `strategyExecutionId` keyBy
- `orderIntentId`별 `OrderLifecycleState` 관리
- partial fill 누적
- full fill 판단
- reject/cancel terminal state 판단
- event id dedup state 추가

완료 기준:

- partial fill 여러 개가 들어와도 milestone은 정확히 한 번 생성된다.
- terminal milestone 이후 late event는 anomaly로 분리된다.

### Phase 4. Timeout and Anomaly Detection

작업:

- submitted 이후 fill timeout timer 등록
- fill-before-submit 감지
- overfill 감지
- conflicting broker order id 감지
- side output 또는 별도 anomaly topic 발행

완료 기준:

- 제출 후 제한 시간 내 체결 이벤트가 없으면 timeout milestone이 생성된다.
- 비정상 순서 이벤트가 main milestone으로 섞이지 않는다.

### Phase 5. Temporal Signal Bridge

작업:

- `strategy-execution-service`에 milestone Kafka listener 추가
- milestone idempotency 저장소 추가
- Temporal Workflow signal use case 추가
- Workflow signal handler 추가

완료 기준:

- `LaorEntryBuyFilled` milestone이 들어오면 workflow가 다음 sell order intent를 만든다.
- milestone 재전달에도 workflow signal 처리 결과가 중복되지 않는다.

### Phase 6. Kubernetes Deployment

작업:

- Flink Kubernetes Operator 설치 문서 추가
- `FlinkDeployment` manifest 추가
- checkpoint/savepoint storage 설정
- job upgrade 정책 정의

완료 기준:

- `kubectl apply`로 Flink job 배포 가능
- job restart 후 checkpoint에서 state 복원
- savepoint 기반 upgrade 절차가 문서화됨

## Testing Strategy

Unit tests:

- event deserialization
- state transition
- deduplication
- partial fill aggregation
- milestone idempotency key generation

Integration tests:

- Kafka input topic -> Flink job -> output topic
- out-of-order event handling
- duplicate event handling
- late event after terminal state

Replay tests:

- fixed sequence of raw order events replay
- expected milestone list comparison
- checkpoint restore 후 동일 결과 확인

Temporal bridge tests:

- milestone event -> workflow signal
- duplicate milestone event skip
- workflow creates next order intent once

## Operational Metrics

Flink metrics:

- input lag
- checkpoint duration
- checkpoint failure count
- backpressure
- milestone output count by type
- anomaly count by type

Application metrics:

- milestone listener success/failure
- workflow signal count
- duplicate milestone skip count
- next order intent creation count

## Risks

### Flink Takes Too Much Responsibility

위험:

- Flink가 전략 DB 변경이나 broker call까지 맡으면 stream processor와 command handler 경계가 무너진다.

대응:

- Flink output은 milestone event로 제한한다.
- source of truth 변경은 `strategy-execution-service`가 담당한다.

### Temporal and Flink Both Model the Same State

위험:

- Flink state와 Temporal workflow state가 서로 다른 결론을 낼 수 있다.

대응:

- Flink state는 raw order lifecycle projection으로 제한한다.
- 전략 상태 최종 판단은 Temporal과 `strategy-execution-service`가 담당한다.

### Event Contract Drift

위험:

- raw order event field가 바뀌면 Flink state 복원과 milestone 생성이 깨질 수 있다.

대응:

- proto versioning과 compatibility test를 추가한다.
- savepoint migration 계획 없이 state schema를 변경하지 않는다.

## Recommendation

Laor 전략에 Flink를 붙인다면 첫 구현 대상은 `Laor Order Lifecycle Aggregator / Milestone Detector`가 가장 적합하다.

이 선택은 다음 조건을 모두 만족한다.

- Flink의 keyed state, timer, checkpoint를 자연스럽게 보여줄 수 있다.
- 실제 broker side effect를 Flink에 넣지 않아 운영 위험이 작다.
- Temporal Workflow는 더 높은 수준의 milestone만 받아 코드가 단순해진다.
- 이벤트 replay로 projection과 milestone 생성 결과를 검증할 수 있다.

최종 구조는 다음 원칙을 따른다.

```text
Flink는 raw event를 milestone으로 정제한다.
Temporal은 Laor 전략 인스턴스의 장기 실행 흐름을 소유한다.
Spring Boot services는 source-of-truth DB 변경과 outbox를 책임진다.
```
