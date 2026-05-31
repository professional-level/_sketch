# Strategy Architecture Analysis

## Purpose

이 문서는 매매 전략이 어떤 서비스에서 시작되고, 어디에서 생명주기를 관리하며, 주문과 체결이 어느 경계에서 처리되는지를 정리한다.

초기 구조는 `stock-search-service`가 전략 후보를 만들고 `stock-purchase-service`가 주문을 생성하는 흐름에 가까웠다. 라오어 무한매수법처럼 여러 거래일에 걸쳐 이어지는 전략이 추가되면서, 전략의 시작, 실행, 주문, 체결 책임을 더 명확히 나눌 필요가 생겼다.

이 문서의 결론은 다음과 같다.

```text
stock-search-service
  = 전략 시작 후보 발견

strategy-execution-service
  = 시작된 전략 인스턴스의 생명주기 관리

stock-purchase-service
  = 주문 제출, 주문 상태, 체결 상태 관리

broker wrapper service
  = KIS Open API 호출 감싸기
```

## Core Decision

모든 전략은 `stock-search-service`에 의해 시작된다.

`stock-search-service`는 주문을 만들지 않는다. 대신 "이 전략을 이 종목으로 시작해도 된다"는 전략 시작 요청을 만든다. 전략 시작 이후에는 `strategy-execution-service`가 전략 인스턴스를 소유한다. `strategy-execution-service`는 현재 전략 상태와 시장 데이터를 기준으로 주문 의도를 생성한다. `stock-purchase-service`는 주문 의도를 실제 broker 주문과 체결 상태로 관리한다.

```text
stock-search-service
  -> StrategyExecutionStartRequested
  -> strategy-execution-service
  -> OrderIntentCreated
  -> stock-purchase-service
  -> broker order / execution tracking
  -> OrderSubmitted / OrderFilled / OrderRejected
  -> strategy-execution-service
```

이 구조에서는 단발성 전략과 장기 운용 전략이 같은 출발점을 가진다. 차이는 execution-service 내부 생명주기의 길이다.

## Service Responsibilities

### stock-search-service

`stock-search-service`는 전략 시작 후보를 발견하는 서비스다.

책임:

- 시장 데이터, 거래대금, 수급, 랭킹, 조건검색 등으로 후보 종목을 탐색한다.
- 특정 전략을 시작할 조건이 충족되었는지 판단한다.
- 전략 시작 요청 이벤트를 발행한다.
- 동일 전략이 같은 종목에서 중복 시작되지 않도록 idempotency key를 만든다.
- 전략 시작에 필요한 초기 파라미터를 전달한다.

하지 말아야 할 일:

- broker 주문을 직접 제출하지 않는다.
- `OrderIntentCreated`를 직접 만들지 않는다.
- 라오어의 진행 회차, 보유 수량, 평균단가 같은 운용 상태를 관리하지 않는다.
- 체결 결과로 전략 상태를 변경하지 않는다.

대표 출력:

```text
StrategyExecutionStartRequested
```

### strategy-execution-service

`strategy-execution-service`는 시작된 전략 인스턴스의 생명주기를 관리하는 서비스다.

책임:

- `StrategyExecutionStartRequested`를 수신해 전략 인스턴스를 등록한다.
- 전략별 상태를 저장하고 갱신한다.
- 매 실행 시점마다 시장 데이터와 현재 상태를 읽는다.
- 전략 알고리즘에 따라 주문 의도를 생성한다.
- `OrderIntentCreated` 이벤트를 발행한다.
- `stock-purchase-service`가 발행한 주문/체결 이벤트를 받아 전략 상태를 갱신한다.
- 전략 종료, 다음 턴 시작, 재시작 여부를 결정한다.

하지 말아야 할 일:

- broker API를 직접 호출하지 않는다.
- 주문번호, 체결번호, broker 원장 reconciliation을 직접 소유하지 않는다.
- 종목 탐색 조건을 직접 수행하지 않는다.

대표 출력:

```text
OrderIntentCreated
StrategyExecutionStarted
StrategyExecutionStateChanged
StrategyExecutionCycleClosed
StrategyExecutionCompleted
```

### stock-purchase-service

`stock-purchase-service`는 주문 제출과 체결 상태를 관리하는 서비스다.

책임:

- `OrderIntentCreated`를 수신한다.
- idempotency key 기준으로 중복 주문을 방지한다.
- 국내/해외 주문 adapter를 선택한다.
- broker 주문을 제출한다.
- broker order id를 내부 주문 id와 매핑한다.
- 주문 상태를 관리한다.
- 체결 조회 또는 broker 체결 이벤트로 fill을 저장한다.
- 체결 결과를 integration event로 발행한다.

하지 말아야 할 일:

- 전략 알고리즘을 판단하지 않는다.
- 라오어 진행 회차나 평균단가를 직접 계산하지 않는다.
- stock-search 조건을 재평가하지 않는다.

대표 출력:

```text
OrderSubmitted
OrderRejected
OrderFilled
OrderPartiallyFilled
OrderCancelled
```

### broker wrapper service

현재 root application의 `OpenApiService`는 KIS Open API wrapper 역할을 한다. 장기적으로는 `kis-openapi-service`, `broker-gateway-service`, 또는 `broker-integration-service`로 명명하는 것이 자연스럽다.

책임:

- KIS token 관리
- KIS TR ID 매핑
- 국내/해외 시세 조회 wrapper
- 국내/해외 주문 wrapper
- KIS response를 내부 응답으로 정규화
- rate limit, retry, timeout, circuit breaker 적용

하지 말아야 할 일:

- 전략 판단을 하지 않는다.
- 주문 intent idempotency를 소유하지 않는다.
- 전략 상태를 저장하지 않는다.

## Strategy Categories

### Single-shot strategy

`FinalPriceBatingV1` 같은 단발성 전략은 한 번의 발견으로 시작되고, 짧은 주문 흐름으로 끝날 수 있다.

이 전략도 `stock-search-service -> stock-purchase-service`로 직접 연결하지 않는다. 시작은 항상 `StrategyExecutionStartRequested`로 통일한다.

```text
stock-search-service
  -> StrategyExecutionStartRequested(FINAL_PRICE_BATING_V1)
  -> strategy-execution-service
  -> OrderIntentCreated(BUY)
  -> stock-purchase-service
  -> OrderSubmitted / OrderFilled
  -> strategy-execution-service
  -> StrategyExecutionCompleted
```

특징:

- execution state가 단순하다.
- 주문이 1회로 끝날 수 있다.
- 매도 정책이 있으면 execution-service가 다음 sell intent를 만든다.
- 체결 후 바로 completed 상태가 될 수 있다.

### Long-running strategy

라오어 무한매수법은 한 번 시작되면 여러 거래일 동안 매수, 부분 매도, 목표 매도, 역매매, 다음 턴을 반복할 수 있다.

```text
stock-search-service
  -> StrategyExecutionStartRequested(LAOR_V4)
  -> strategy-execution-service
  -> FIRST_BUY OrderIntentCreated
  -> stock-purchase-service
  -> OrderFilled
  -> strategy-execution-service
  -> state updated
  -> next trading day execution
  -> additional buy/sell intents
```

특징:

- 시작 조건은 `stock-search-service`가 판단한다.
- 시작 이후 운용은 `strategy-execution-service`가 판단한다.
- 매일의 주문 여부는 `stock-search-service`가 재판단하지 않는다.
- 체결 이벤트를 기준으로 전략 상태가 변한다.
- 전량 매도 후 `autoRestart` 정책에 따라 다음 턴으로 돌아갈 수 있다.

## LAOR Lifecycle

라오어 전략 인스턴스는 하나의 symbol과 budget을 가진 실행 단위다.

### 1. Candidate discovered

`stock-search-service`가 라오어를 시작할 수 있는 후보를 발견한다.

예시:

```text
strategyType = LAOR_V4
symbol = TQQQ
budget = 10000
totalSplitCount = 20
firstBuyLimitMultiplier = 1.12
idempotencyKey = LAOR_V4:TQQQ:2026-05-31
```

이 시점에는 주문이 만들어지지 않는다.

### 2. Strategy execution registered

`strategy-execution-service`가 시작 요청을 받아 실행 인스턴스를 만든다.

초기 상태:

```text
mode = NORMAL
progressRound = 0
availableCash = budget
holdingQuantity = 0
averagePurchasePrice = 0
reverseModeElapsedDays = 0
cycleNo = 1
status = ACTIVE
```

### 3. First entry planned

보유 수량이 0이고 진행 회차가 0이면 첫 진입 주문을 계획한다.

현재 구현 기준:

```text
FIRST_BUY
side = BUY
orderType = LOC
price = previousClose * firstBuyLimitMultiplier
budget = availableCash / totalSplitCount
```

### 4. Order submitted

`stock-purchase-service`가 `OrderIntentCreated`를 수신하고 broker 주문을 제출한다.

필수 저장:

```text
internalOrderId
strategyExecutionId
orderIntentId
brokerOrderId
symbol
side
orderType
submittedPrice
quantity
status
```

### 5. Fill applied

체결이 확인되면 `stock-purchase-service`가 fill event를 발행한다.

```text
OrderFilled
strategyExecutionId
orderIntentId
brokerOrderId
side
filledPrice
filledQuantity
orderTag
filledAt
```

`strategy-execution-service`는 이 이벤트를 받아 라오어 상태에 반영한다.

매수 체결 시:

- `availableCash` 감소
- `holdingQuantity` 증가
- `averagePurchasePrice` 재계산
- `progressRound` 증가

매도 체결 시:

- `availableCash` 증가
- `holdingQuantity` 감소
- realized P/L 계산
- 전량 매도 여부 판단

### 6. Daily execution

다음 거래일부터는 `strategy-execution-service`가 active strategy를 실행한다.

입력:

```text
current strategy state
previous close
recent close prices
trading calendar
```

출력:

```text
zero or more OrderIntentCreated
```

주문이 없는 날도 정상 상태다.

### 7. Cycle closed

보유 수량이 0이 되면 한 턴이 종료된다.

정책:

```text
autoRestart = true
  -> mode = NORMAL
  -> progressRound = 0
  -> averagePurchasePrice = 0
  -> cycleNo += 1
  -> next trading day can create FIRST_BUY again

autoRestart = false
  -> status = COMPLETED
```

현재 도메인 엔진은 `holdingQuantity == 0`이면 normal state로 리셋하는 로직을 가지고 있다. 다만 실제 fill event와 이 로직을 연결하는 작업이 아직 필요하다.

## Start Request Contract

전략 시작 이벤트는 주문 이벤트보다 상위 개념이다.

```text
message StrategyExecutionStartRequested {
  string event_id = 1;
  string idempotency_key = 2;
  StrategyExecutionType strategy_type = 3;
  string strategy_version = 4;
  string symbol = 5;
  string market = 6;
  double budget = 7;
  string source_service = 8;
  string source_signal_id = 9;
  google.protobuf.Timestamp requested_at = 10;
  StrategyExecutionParameters parameters = 11;
}
```

전략별 파라미터는 `oneof`가 가장 명시적이다.

```text
message StrategyExecutionParameters {
  oneof value {
    LaorV4StartParameters laor_v4 = 1;
    FinalPriceBatingV1StartParameters final_price_bating_v1 = 2;
  }
}
```

라오어 파라미터:

```text
message LaorV4StartParameters {
  int32 total_split_count = 1;
  double first_buy_limit_multiplier = 2;
  bool auto_restart = 3;
}
```

단발성 전략 파라미터:

```text
message FinalPriceBatingV1StartParameters {
  double target_buy_price = 1;
  double budget = 2;
  string quantity_policy = 3;
}
```

## Order Intent Contract

`OrderIntentCreated`는 strategy-execution-service가 purchase-service에 보내는 주문 의도다.

필수 속성:

```text
eventId
strategyExecutionId
strategyType
symbol
market
side
orderType
price
quantity
orderTag
idempotencyKey
createdAt
```

중요 원칙:

- 같은 `idempotencyKey`는 같은 주문 의도를 의미한다.
- purchase-service는 같은 intent를 두 번 주문하지 않는다.
- execution-service는 broker order id를 직접 알 필요는 없지만, 체결 이벤트와 매칭할 수 있도록 `strategyExecutionId`, `orderIntentId`, `orderTag`를 유지해야 한다.

## Execution Event Contract

purchase-service는 주문/체결 결과를 execution-service로 되돌려야 한다.

최소 이벤트:

```text
OrderSubmitted
OrderRejected
OrderFilled
OrderPartiallyFilled
```

`OrderSubmitted`:

```text
strategyExecutionId
orderIntentId
brokerOrderId
submittedAt
```

`OrderFilled`:

```text
strategyExecutionId
orderIntentId
brokerOrderId
side
filledPrice
filledQuantity
orderTag
filledAt
```

이 이벤트가 있어야 strategy-execution-service가 단발성 전략 종료, 라오어 진행 회차 증가, 전량 매도 후 다음 턴 전환을 정확히 할 수 있다.

## Idempotency Rules

### Start request idempotency

`stock-search-service`는 같은 전략 시작 후보에 대해 안정적인 key를 만든다.

예:

```text
LAOR_V4:TQQQ:2026-05-31
FINAL_PRICE_BATING_V1:005930:2026-05-31
```

`strategy-execution-service`는 같은 key를 다시 받으면 새 실행 인스턴스를 만들지 않는다.

### Order intent idempotency

`strategy-execution-service`는 실행 run, 전략 인스턴스, order tag, order index를 조합해 key를 만든다.

예:

```text
laor-v4:TQQQ:run-20260531:FIRST_BUY:0
```

`stock-purchase-service`는 이 key를 processed-event 또는 order table에 저장해 중복 주문을 방지한다.

### Broker submission idempotency

broker API 자체가 idempotency key를 지원하지 않는다면, purchase-service 내부에서 다음 순서를 지켜야 한다.

```text
1. intent 수신
2. local order row 생성, status = SUBMITTING
3. broker submit
4. brokerOrderId 저장, status = SUBMITTED
5. 실패 시 status = SUBMIT_FAILED 또는 SUBMISSION_UNKNOWN
```

`SUBMISSION_UNKNOWN`은 재시도 전에 broker 주문 조회가 필요하다.

## Current Implementation Status

현재 구현된 것:

- `strategy-execution-service`에 라오어 도메인 엔진이 있다.
- 라오어 active strategy 등록 API가 있다.
- 라오어 active strategy 실행 use case가 있다.
- 해외 일봉 조회 wrapper 경로가 있다.
- `OrderIntentCreated` Kafka 발행 경로가 있다.
- `stock-purchase-service`가 `OrderIntentCreated`를 소비한다.
- 해외 미국 매수 주문 wrapper가 있다.
- purchase-service 내부에 국내/해외 주문 adapter 분리가 시작되었다.

아직 부족한 것:

- `stock-search-service`가 `StrategyExecutionStartRequested`를 발행하지 않는다.
- `strategy-execution-service`가 start request를 수신하는 adapter가 없다.
- 단발성 전략이 아직 execution-service 생명주기로 이관되지 않았다.
- broker order id를 내부 주문 상태와 안정적으로 매핑하는 흐름이 부족하다.
- purchase-service가 체결 이벤트를 strategy-execution-service로 되돌리는 흐름이 부족하다.
- 라오어 `applyFills()`가 실제 체결 이벤트와 연결되지 않았다.
- active strategy state가 아직 영속 저장소가 아니다.
- 해외 매도 주문은 아직 연결되지 않았다.

## Migration From Legacy Flow

기존 흐름:

```text
stock-search-service
  -> StrategySavedEvent
  -> stock-purchase-service
  -> strategy-specific purchase handler
```

목표 흐름:

```text
stock-search-service
  -> StrategyExecutionStartRequested
  -> strategy-execution-service
  -> OrderIntentCreated
  -> stock-purchase-service
```

이관 기준:

- `StrategySavedEvent`는 발견 결과 저장 또는 legacy event로 축소한다.
- 주문 생성 판단은 purchase-service에서 execution-service로 이동한다.
- `BuyingStockPurchaseUseCase` 계열은 단발성 전략 이관이 끝나면 `OrderIntentCreated` consumer 중심으로 축소한다.
- purchase-service는 전략 타입별 핸들러 대신 주문 intent와 broker adapter 중심으로 단순화한다.

## Implementation Order

1. `StrategyExecutionStartRequested` proto/event 계약을 추가한다.
2. `stock-search-service`가 기존 `StrategySavedEvent` 대신 또는 병행해서 start request를 발행한다.
3. `strategy-execution-service`에 start request consumer를 추가한다.
4. 단발성 전략도 execution-service에 실행 모델을 만든다.
5. `strategy-execution-service`가 첫 실행에서 `OrderIntentCreated`를 발행하게 한다.
6. purchase-service가 broker order id를 저장하고 internal order와 매핑한다.
7. purchase-service가 `OrderSubmitted`, `OrderFilled`, `OrderRejected` 이벤트를 발행한다.
8. strategy-execution-service가 fill event를 수신해 전략 상태를 갱신한다.
9. 라오어 cycle close와 auto restart 정책을 명시적으로 저장한다.
10. active strategy state를 persistence adapter로 옮긴다.
11. Temporal schedule 또는 application scheduler로 daily execution trigger를 붙인다.

## Open Questions

- 라오어는 전량 매도 후 항상 다음 턴을 시작하는가, 아니면 search-service가 다시 시작 조건을 승인해야 하는가?
- 단발성 전략의 매도 정책은 execution-service가 소유할 것인가, purchase-service의 기존 scheduler를 단계적으로 유지할 것인가?
- `StrategyExecutionStartRequested`를 Kafka 이벤트로만 받을 것인가, 내부 운영용 REST endpoint도 둘 것인가?
- 체결 조회 주기는 purchase-service scheduler가 가질 것인가, broker webhook이 가능하면 webhook을 우선할 것인가?
- 해외 주문에서 모의투자와 실전투자의 주문 유형 차이를 strategy parameter로 노출할 것인가, adapter 정책으로 숨길 것인가?

## Final Shape

최종 구조는 다음과 같다.

```text
Discovery phase
  stock-search-service
    -> StrategyExecutionStartRequested

Execution phase
  strategy-execution-service
    -> strategy instance lifecycle
    -> first entry
    -> daily execution
    -> cycle close / restart
    -> OrderIntentCreated

Order phase
  stock-purchase-service
    -> submit broker order
    -> track broker order status
    -> reconcile fills
    -> publish execution result

Broker integration phase
  broker wrapper service
    -> KIS token / TR ID / raw API contract
```

이 구조에서는 모든 전략이 같은 출발점을 가진다. 전략마다 실행 기간만 다르다. 단발성 전략은 execution-service에서 짧게 종료되고, 라오어 같은 장기 전략은 같은 execution-service 안에서 여러 거래일 동안 상태를 이어간다. purchase-service는 전략을 모르는 주문/체결 서비스로 유지된다.
