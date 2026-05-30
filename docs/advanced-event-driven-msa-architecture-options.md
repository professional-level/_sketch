# Advanced Event-Driven MSA Architecture Options

## 목적

이 문서는 현재 stock trading MSA sketch에서 `@Scheduled` 기반 흐름을 더 고급 이벤트 기반 구조로 발전시킬 때 검토할 수 있는 기술 옵션을 정리한다. 특정 기술 도입을 확정하는 문서가 아니라, 각 기술이 어떤 책임을 맡을 수 있고 어떤 흐름으로 연결될 수 있는지 인지하기 위한 기술 메모다.

프로젝트가 학습 목적을 가진다는 점을 고려해, 단순 CRUD MSA를 넘어서 시간, 이벤트 순서, 상태 복원, 재처리, 관측 가능성을 다루는 방향을 중심으로 정리한다.

## 핵심 관점

`@Scheduled`는 단일 애플리케이션 내부에서는 간단하지만, MSA 환경에서는 다음 문제가 생긴다.

- 여러 replica에서 같은 scheduler가 중복 실행될 수 있다.
- 실행 이력, pause/resume, backfill, retry 정책이 애플리케이션 코드 안으로 섞인다.
- 장애 후 어디까지 처리되었는지 복구하기 어렵다.
- 스케줄 트리거, 도메인 실행, 이벤트 발행, 상태 변경 책임이 한 클래스에 모이기 쉽다.

고급 이벤트 기반 구조에서는 scheduler를 애플리케이션 내부 기능으로 두기보다, 외부 orchestration 또는 durable workflow 계층에서 시간 트리거를 만들고 MSA는 command/event를 멱등하게 처리하는 worker가 된다.

## 기술별 책임 후보

| 기술 | 책임 후보 | 이 프로젝트에서의 의미 |
| --- | --- | --- |
| Temporal | 시간 기반 workflow, retry, backfill, pause/resume, 장기 실행 orchestration | `DiscoveryRunWorkflow`, 장중 전략 실행 workflow, 수동 재실행 |
| Kafka | durable event log, 서비스 간 비동기 계약, replay 가능한 이벤트 저장소 | `DiscoveryRunRequested`, `StrategySignalCreated`, `OrderIntentCreated`, `ExecutionFilled` |
| Flink | stateful stream processing, event-time window, 집계, join, pattern detection | 시세/체결/전략 상태 집계, 실시간 ranking, partial fill 합산 |
| Kafka Streams | Kafka 중심의 경량 stateful processor | purchase-service 내부 체결 중복 제거, 간단한 상태 집계 |
| Debezium Outbox | DB transaction과 Kafka publish의 신뢰성 연결 | 도메인 저장과 이벤트 발행 사이의 dual-write 위험 감소 |
| CQRS Projection | 이벤트에서 조회용 read model 생성 | 주문 현황, 전략 상태, 포트폴리오, PnL 화면 |
| OpenTelemetry | trace, metrics, logs, context propagation | schedule -> signal -> order -> fill 전체 추적 |

## 후보 아키텍처 흐름

```text
Temporal Schedule
  -> DiscoveryRunWorkflow
  -> DiscoveryRunRequested event

Kafka
  -> discovery-run-requested topic

stock-search-service
  -> DiscoveryRunRequested consume
  -> DiscoveryStrategy 실행
  -> StrategySignalCreated 저장
  -> Outbox insert

Debezium
  -> outbox table capture
  -> strategy-signal-created topic publish

strategy-execution-service
  -> StrategySignalCreated consume
  -> TradingStrategyExecution.generateOrders()
  -> OrderIntentCreated 저장
  -> Outbox insert

stock-purchase-service
  -> OrderIntentCreated consume
  -> broker order submit/manage

broker/execution ingestion
  -> ExecutionFilled event publish

Flink or Kafka Streams
  -> ExecutionFilled, OrderIntentCreated, StrategyStateChanged consume
  -> partial fill 집계
  -> LaorV4Strategy state transition
  -> StrategyStateChanged / RiskAlert publish

Projection service
  -> read model 생성
  -> API/UI query 제공
```

이 흐름의 핵심은 각 기술의 역할을 분리하는 것이다.

- Temporal은 시간과 절차를 다룬다.
- Kafka는 이벤트의 영속 로그를 다룬다.
- Flink/Kafka Streams는 흘러가는 이벤트의 상태 계산을 다룬다.
- Spring Boot MSA는 도메인 command 처리와 port/adapter orchestration에 집중한다.
- DB는 aggregate state, read model, outbox를 저장한다.

## 이벤트 모델 후보

초기 후보 이벤트는 다음처럼 나눌 수 있다.

```text
DiscoveryRunRequested
MarketSnapshotCaptured
StrategySignalCreated
OrderIntentCreated
OrderSubmitted
OrderAccepted
OrderRejected
ExecutionFilled
StrategyStateChanged
RiskAlertRaised
```

중요한 설계 포인트는 event key다.

| 이벤트 | key 후보 | 이유 |
| --- | --- | --- |
| `DiscoveryRunRequested` | `runId` | 같은 run의 중복 실행 방지 |
| `StrategySignalCreated` | `strategyInstanceId` 또는 `symbol` | 전략별 순서 보장 |
| `OrderIntentCreated` | `orderId` | 주문 상태 전이 순서 보장 |
| `ExecutionFilled` | `externalOrderId` 또는 `orderId` | partial fill 합산과 중복 제거 |
| `StrategyStateChanged` | `strategyInstanceId` | 전략 상태의 단일 writer 모델 구성 |

Kafka partition key를 잘못 잡으면 순서 보장이 깨진다. 자동매매 도메인에서는 전역 순서보다 `strategyInstanceId`, `orderId`, `symbol` 같은 비즈니스 단위 순서가 더 중요하다.

## Temporal 검토 포인트

Temporal은 cron 대체라기보다 durable execution 계층으로 볼 수 있다.

적합한 영역:

- 장 시작/종료 기준 workflow 실행
- 특정 날짜 또는 특정 전략의 backfill
- 실패한 discovery run 재시도
- 수동 승인 후 주문 실행
- workflow history 기반 디버깅

주의할 점:

- workflow code는 deterministic해야 한다.
- 외부 API 호출, DB 접근, Kafka publish는 activity로 분리해야 한다.
- 이미 Kafka/Flink로 충분한 단순 이벤트 처리까지 Temporal로 감싸면 orchestration이 과해질 수 있다.

## Flink 검토 포인트

Flink는 단일 이벤트 처리보다 여러 이벤트를 기억해야 하는 stateful 처리에 가치가 있다.

적합한 영역:

- 최근 N분 거래대금 ranking
- event-time 기준 시세 window 계산
- 체결 이벤트 partial fill 합산
- 전략별 position, avgPrice, realizedPnl 실시간 계산
- `LaorV4Strategy` 상태 전이 검증
- 과거 이벤트 replay를 통한 backtest

주의할 점:

- Flink job은 별도 runtime과 운영 모델을 가진다.
- schema evolution, checkpoint, savepoint, state migration을 함께 학습해야 한다.
- 단순 consumer use case에는 Kafka consumer나 Kafka Streams가 더 간단할 수 있다.

## Kafka Streams 검토 포인트

Kafka Streams는 Kafka 중심 시스템에서 경량 stateful processing을 넣을 때 자연스럽다.

적합한 영역:

- `strategy-execution-service` 내부 또는 인접 모듈에서 체결 이벤트 dedup
- 주문별 partial fill 합산
- 간단한 KTable 기반 전략 상태 projection
- Kafka input offset, state store, output topic을 한 처리 단위로 묶는 exactly-once processing 실험

주의할 점:

- Kafka 외부 DB나 broker API side effect까지 exactly-once가 되는 것은 아니다.
- 외부 주문 API 호출은 idempotency key와 outbox/command table이 필요하다.
- 상태 저장소 복구, RocksDB, changelog topic 운영을 이해해야 한다.

## Debezium Outbox 검토 포인트

서비스가 DB 저장 후 Kafka publish에 실패하면 도메인 상태와 이벤트 로그가 어긋난다. Outbox는 이 dual-write 문제를 줄이기 위한 선택지다.

흐름:

```text
application transaction
  -> aggregate 저장
  -> outbox row insert

Debezium connector
  -> outbox row capture
  -> Kafka topic publish
```

장점:

- 도메인 상태 변경과 이벤트 발행 요청을 같은 DB transaction에 넣을 수 있다.
- 서비스 코드에서 Kafka publish retry를 직접 들고 있지 않아도 된다.
- Kafka topic이 도메인 이벤트 로그로 안정화된다.

주의할 점:

- connector 운영이 추가된다.
- outbox table cleanup 정책이 필요하다.
- event schema와 topic routing 규칙을 엄격히 관리해야 한다.

## Replayable Backtest 방향

학습용 프로젝트에서 가장 가치 있는 고급 기능은 replay다.

목표:

- 특정 날짜의 market data event를 다시 재생한다.
- 특정 strategy version으로 signal을 다시 계산한다.
- 실제 체결 이벤트와 전략 예상 상태를 비교한다.
- `LaorV4Strategy`의 상태를 event log만으로 재구성한다.

가능한 흐름:

```text
historical-market-events
  -> replay controller
  -> Flink/Kafka Streams backtest topology
  -> simulated-order-events
  -> strategy-state-projection
```

이 기능이 생기면 단순 자동매매 sketch가 아니라, 전략 검증과 장애 복구를 설명할 수 있는 event-driven platform이 된다.

## Observability 방향

이 구조에서는 로그만으로 원인을 찾기 어렵다. 처음부터 trace context를 이벤트 header에 포함하는 것이 좋다.

추적하고 싶은 흐름:

```text
DiscoveryRunRequested
  -> StrategySignalCreated
  -> OrderIntentCreated
  -> OrderSubmitted
  -> ExecutionFilled
  -> StrategyStateChanged
```

관측 항목:

- trace id, span id
- Kafka topic, partition, offset
- event id, causation id, correlation id
- strategyInstanceId, orderId, externalOrderId
- retry count, processing latency, consumer lag

## 단계적 도입 후보

아래 순서는 확정 로드맵이 아니라 학습 난이도와 구조적 효과를 고려한 후보 순서다.

1. 현재 `@Scheduled` 로직을 use case로 분리한다.
2. scheduler는 `DiscoveryRunRequested` 이벤트만 발행하도록 얇게 만든다.
3. Kafka event naming, key, schema 규칙을 정리한다.
4. `stock-search-service`, `strategy-execution-service`, `stock-purchase-service`를 event consumer/producer 중심으로 정렬한다.
5. Outbox table과 Debezium publish 흐름을 실험한다.
6. Temporal Schedule로 내부 scheduler를 대체하는 실험을 한다.
7. purchase 체결 처리에 Kafka Streams를 붙여 partial fill 집계를 구현한다.
8. Flink job으로 market data window와 strategy state projection을 구현한다.
9. replay/backtest mode를 추가한다.
10. OpenTelemetry로 event-driven trace propagation을 구성한다.

## 판단 기준

도입 여부는 기술 자체보다 문제가 충분히 존재하는지로 판단한다.

| 문제가 있다면 | 검토할 기술 |
| --- | --- |
| cron 실행 이력, retry, backfill, pause/resume이 필요하다 | Temporal |
| 서비스 간 이벤트 로그와 replay가 필요하다 | Kafka |
| DB 저장과 이벤트 발행의 원자성이 문제다 | Debezium Outbox |
| 여러 체결 이벤트를 모아 상태를 계산해야 한다 | Kafka Streams |
| event-time window, 대량 stream join, checkpoint 기반 복구가 필요하다 | Flink |
| 이벤트 흐름 추적이 어렵다 | OpenTelemetry |

## 참고 자료

- Temporal Documentation: https://docs.temporal.io/
- Apache Kafka Streams Core Concepts: https://kafka.apache.org/35/streams/core-concepts/
- Apache Flink Stateful Stream Processing: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/concepts/stateful-stream-processing/
- Debezium Outbox Event Router: https://debezium.io/documentation/reference/3.4/transformations/outbox-event-router.html
- OpenTelemetry Documentation: https://opentelemetry.io/docs/
