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
tradingEnvironment
```

중요 원칙:

- 같은 `idempotencyKey`는 같은 주문 의도를 의미한다.
- purchase-service는 같은 intent를 두 번 주문하지 않는다.
- execution-service는 broker order id를 직접 알 필요는 없지만, 체결 이벤트와 매칭할 수 있도록 `strategyExecutionId`, `orderIntentId`, `orderTag`를 유지해야 한다.
- `tradingEnvironment`는 `MOCK` 또는 `LIVE` 기대값을 명시한다. purchase-service는 이 값이 있으면 broker route의 실제 mock/live 설정과 대조하고, 없으면 기존 strategy prefix risk policy로 fallback한다.

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

검토 기준: 2026-06-02

현재 구현된 것:

- `StrategyExecutionStartRequested`, `OrderIntentCreated`, `OrderSubmitted`, `OrderRejected`, `OrderCancelled`, `OrderFilled`, `OrderPartiallyFilled` proto/event 계약이 있다.
- `stock-search-service`는 전략 발견 결과를 outbox에 저장하고 `StrategyExecutionStartRequested`로 발행한다.
- `strategy-execution-service`는 start request Kafka 이벤트를 수신하고, idempotency key 기준으로 중복 시작을 방지한다.
- `strategy-execution-service`는 라오어 V4 실행 상태를 persistence adapter에 저장한다.
- 라오어 V4 등록, 첫 진입, active strategy 실행 use case가 있다.
- 단발성 `FinalPriceBatingV1`도 start request를 통해 entry buy `OrderIntentCreated`를 만들 수 있다.
- `strategy-execution-service`는 `OrderIntentCreated`를 Kafka로 발행한다.
- `stock-purchase-service`는 `OrderIntentCreated`를 소비하고 processed-event 저장소로 중복 주문을 방지한다.
- `stock-purchase-service`는 broker order id와 internal order intent submission을 매핑한다.
- 해외 미국 매수/매도 주문 adapter 경로가 있다.
- `stock-purchase-service`는 주문 제출 성공/실패를 `OrderSubmitted`, `OrderRejected`로 발행한다.
- `stock-purchase-service`는 체결 reconciliation 결과를 `OrderPartiallyFilled` 또는 `OrderFilled`로 구분해 발행한다.
- `stock-purchase-service`는 broker 주문 취소 확인 시 `OrderCancelled`를 발행한다.
- `stock-purchase-service`는 broker 제출 결과가 불명확한 order intent를 `SUBMISSION_UNKNOWN`으로 저장하고, recovery use case에서 broker 상태를 다시 조회해 submitted/rejected/cancelled로 확정한다. 취소 요청은 accepted/unknown 시 원 주문 제출 row를 `CANCEL_PENDING`으로 보존하고, broker 조회가 실제 cancelled를 확인한 뒤에만 `OrderCancelled`를 발행한다. 복구 조회 실패는 주문별로 격리해 해당 row를 pending 상태로 유지하고 알림을 남기며, 다른 pending 주문 복구를 계속 진행한다.
- `stock-purchase-service`의 broker gateway anti-corruption layer는 KIS wrapper의 주문 제출, 1일 주문/체결 조회 응답을 내부 broker status와 execution DTO로 정규화한다.
- KIS 누적 체결 수량(`tot_ccld_qty`, `ft_ccld_qty`)은 reconciliation 단계에서 이미 저장된 수량을 빼고 신규 delta만 `OrderPartiallyFilled` 또는 `OrderFilled`로 발행한다.
- full fill 판단은 같은 broker order id의 누적 체결 수량이 원 주문 수량 이상인지로 한다.
- `strategy-execution-service`는 `OrderSubmitted`, `OrderRejected`, `OrderPartiallyFilled`, `OrderFilled`를 수신해 주문 이벤트를 idempotent하게 기록한다.
- `strategy-execution-service`는 `OrderCancelled`를 수신해 주문 이벤트를 idempotent하게 기록한다.
- `OrderPartiallyFilled`는 현금, 보유 수량, 평균단가에는 반영하지만 라오어 진행 회차는 증가시키지 않는다.
- 최종 `OrderFilled`는 주문 태그 기준 단위만큼 라오어 진행 회차를 증가시킨다.
- 라오어 V4 주문 수량은 온주 기준 `floor(orderAmount / orderPrice)`로 계산하고, 1주 미만이면 주문을 만들지 않는다.
- 라오어 cycle close, `autoRestart`, `cycleNo`, `COMPLETED` 상태가 execution state에 저장된다.
- `strategy-execution-service`는 Temporal Schedule로 active strategy daily execution workflow를 등록한다.
- scheduled workflow는 실행일 기준 `ACTIVE_STRATEGIES_DAILY:yyyy-MM-dd` execution run id를 만들고, 같은 run id를 이미 처리한 active strategy는 다시 실행하지 않는다.
- `stock-purchase-service`는 체결 reconciliation 시작/성공/실패 상태를 durable cursor로 저장한다.
- 내부 주문과 매칭되지 않는 broker execution은 `unmatched_execution` 저장소에 보존하고 운영 알림으로 노출한다. 이 경우 체결을 `execution_fill`에 확정 저장하지 않으므로, order intent submission이 늦게 복구되면 다음 rolling backfill reconciliation에서 다시 매칭해 fill event를 발행할 수 있다.
- reconciliation 실패 시 cursor를 completed로 전진시키지 않고 failed 상태와 실패 사유를 기록한다.

현재 남은 것:

- `stock-purchase-service`의 체결 reconciliation은 cursor와 unmatched 저장, KIS wrapper 기반 주문/체결 조회 매핑, cursor 기준 rolling 날짜 범위 backfill, KIS 조회 cursor pagination을 갖췄다. 미매칭 체결은 fill로 확정 저장하지 않아 늦게 복구된 order intent submission과 재매칭될 수 있다. KIS wrapper 조회성 호출에는 설정 기반 retry/backoff를 적용했고, wrapper 호출 전 rate-limit와 circuit breaker도 적용한다.
- legacy direct sell submission은 order intent lifecycle 경로로 통일되었다.
- `strategy-execution-service`의 `OrderIntentCreated` 발행과 `stock-purchase-service`의 주문/체결 이벤트 발행은 outbox 저장 후 scheduled publisher가 Kafka로 발행한다.
- stock-purchase-service의 주문/조회 KIS 원문 계약은 `adapter/out/broker`의 broker gateway anti-corruption layer로 격리되었다. 주문 제출 POST는 중복 주문을 피하기 위해 retry하지 않고 transient 실패나 broker order id 누락을 `SUBMISSION_UNKNOWN`으로 보낸다. KIS 주문 응답의 `rt_cd != 0`은 `msg_cd/msg1`을 보존한 broker rejection으로 분리해 `OrderRejected`와 rejected submission으로 확정한다. root wrapper는 KIS 표준 주문 응답 객체의 top-level `rt_cd`, `msg_cd`, `msg1`과 nested `output` 객체/배열의 `ODNO`를 stock-purchase-service용 protobuf로 정규화한다. root wrapper의 국내 주문체결조회 protobuf 변환은 `output1`/`output` 배열/단일 객체/누락, `output2` 객체/배열/누락, 대문자/camel/주문번호 identity alias를 허용하고 `ODNO`, `ord_no`, `KRX_FWDG_ORD_ORGNO`, `ord_orgno`, `THCO_ORD_TMD`, `PRDT_CODE`, `SLL_BUY_DVSN_NAME`, `AVG_PRVS`, `CCLD_UNPR`, `ccld_no`, `rjct_qty`, `cncl_yn`, `cncl_cfrm_qty`, `tot_ccld_qty`, `rmn_qty`, 상태명, 거절사유 같은 reconciliation 핵심 필드를 fixture test로 고정한다. stock-purchase adapter는 국내 상태명/거절사유 alias와 side name fallback도 내부 broker status 및 체결 타입 판정에 반영한다. root wrapper의 국내 주문, 국내 주문체결조회, 국내 정정취소가능조회 HTTP 계약은 KIS path/TR ID/body/query 테스트로 고정했고, 정정취소가능조회는 mock `VTTC0084R`와 live `TTTC0084R`를 분리한다. 조회용 빈 파라미터는 문자 `"\"\""`가 아니라 실제 공란으로 전송한다. 해외 주문체결조회는 KIS `ODNO` 검색 불가 제약 때문에 일자/종목 범위로 조회한 뒤 응답 row의 broker order id를 client-side에서 매칭한다. 해외 취소 precheck와 상태 복구는 현재 주문번호뿐 아니라 KIS `ORGN_ODNO` 원주문번호도 같은 broker 주문으로 매칭한다. `OrderIntentCreated.exchange`와 `order_intent_submission.exchange`는 KIS 해외 `OVRS_EXCG_CD`로 전달되며, 제출 이후 취소와 상태 복구 조회도 저장된 exchange를 재사용한다. SUBMISSION_UNKNOWN/CANCEL_PENDING 복구 조회는 설정 기반 주문 상태 조회 window(`akra.order.status-lookup.backfill-days`, `akra.order.status-lookup.forward-days`)를 적용하고, 저장된 branch/order-org 번호가 있으면 KIS `ordGnoBrno` 조회 조건에도 전달한다. 해외 주문체결조회와 잔고조회는 top-level `output`/`output1`/`output2`/cursor의 대소문자 alias를 허용한다. 해외 row 매핑은 `ODNO/odno`, `ORD_QTY/ft_ord_qty/OVRS_ORD_QTY`, `TOT_CCLD_QTY/ft_ccld_qty/OVRS_CCLD_QTY`, `RMN_QTY/nccs_qty/OVRS_NCCS_QTY`, `AVG_PRVS/ft_ccld_unpr3/OVRS_CCLD_UNPR` 같은 wrapper alias를 허용하고, 한글/영문 rejected/cancelled 상태명을 내부 주문 상태로 정규화한다. KIS wrapper 호출에는 local rate-limit와 circuit breaker가 있고, circuit open/rate-limit 초과처럼 broker에 닿지 않은 실패는 broker rejection 이벤트로 발행하지 않는다. KIS 조회 응답의 non-zero business failure는 broker return/message code를 snake/camel/full-name alias로 보존하며, `EGW00201` 초당 거래건수 초과는 query retry/backoff 대상으로 분류한다. root wrapper의 KIS token은 real/mock scope별 만료 기반 cache, optional local-file persistence, optional JDBC shared token store, JDBC refresh TTL lock으로 관리한다. 별도 broker wrapper service 배포와 secret manager 연동은 아직 남아 있다.
- `strategy-execution-service`는 `OrderIntentCreated.tradingEnvironment`를 기본값 또는 strategy prefix 설정으로 채우고, `stock-purchase-service`는 broker 제출 전 risk guard로 주문 단위 금액 한도, 종목별 주문 금액 한도, 활성 매수 주문 기준 계좌 pending exposure 한도, KIS 국내/해외 계좌 스냅샷 기반 계좌 exposure 한도, KIS 계좌 스냅샷의 broker orderable cash 기반 현금 사용 한도, 하루 broker 제출 건수 한도, 동일 전략/종목/태그 중복 kill switch, 전략 prefix enable allow-list/disable, order intent 및 전략 prefix 기반 mock/live broker route 검증을 적용한다. 계좌 exposure와 하루 주문 건수 평가는 설정 market 목록에 현재 주문 market이 빠져 있어도 현재 market을 항상 포함한다. 기대 trading environment는 `order_intent_submission.tradingEnvironment`에 저장한다.
- `stock-purchase-service`는 broker gateway를 통해 KIS 국내/해외 주식 잔고조회(`inquire-balance`)를 호출하고, `/operations/trading/account-snapshot`에서 포지션 수량, 평균매입가, 현재가, 매입금액, 평가금액, 평가손익, legacy available cash, cash currency, orderable/settled/withdrawable cash bucket을 조회할 수 있다.
- KIS alias 기반 cash bucket 분리와 risk guard 내부 기준 통화 환산은 추가됐다. FX source는 static 설정, HTTP provider, root wrapper의 KIS overseas daily chart price 기반 provider로 분리됐다. 통화쌍별 KIS symbol 운영 검증과 계좌 현금 상태 이벤트 계약 확장은 아직 남아 있다.
- `stock-purchase-service`는 주문 제출 실패, `SUBMISSION_UNKNOWN` 지속, reconciliation 실패, 미매칭 broker execution을 `OperationalAlertPort`로 알리고, 기본 구현은 로그 기반 alert adapter, Micrometer operational alert counter, optional generic webhook, Slack incoming webhook, PagerDuty Events API v2 sink로 둔다. 운영 알림 payload는 MDC의 `traceId`/`spanId`/`traceparent`를 함께 전파한다.
- `strategy-execution-service`는 `/strategy-executions/laor-v4`, `/strategy-executions/laor-v4/{executionId}`, `/strategy-executions/final-price-bating-v1`, `/strategy-executions/final-price-bating-v1/{executionId}`에서 전략별 T/현금/보유/평단 또는 최종가 베팅 매수/매도 체결 상태와 현재 현금/보유/평단을 조회할 수 있다. `/operations/strategy-execution/status`는 order-intent outbox, start request, order event, 전략 lifecycle count를 운영 상태로 노출한다.
- `stock-search-service`, `stock-purchase-service`, `strategy-execution-service`는 production-like profile에서 로컬 Temporal/broker endpoint, mock 주문 설정, 잘못된 Temporal schedule, 또는 로컬 `application-secret.properties` property source가 남아 있으면 startup validation으로 실패한다. `stock-purchase-service`는 비어 있거나 로컬인 운영 알림 route도 차단한다. root KIS wrapper도 production-like profile에서 로컬 secret property source와 unsafe token persistence/duration/lock 설정을 기본 차단한다.
- root sketch app의 KIS secret key 이름은 `src/main/resources/application-secret.properties.example` 템플릿으로 문서화했고, classpath secret 파일 없이 런타임 환경변수/secret source로도 주입할 수 있다. production-like profile에서는 root wrapper, stock-search-service, stock-purchase-service, strategy-execution-service 모두 로컬 `application-secret.properties` property source가 로드되면 startup validation으로 실패한다. 운영 재시작 절차와 token/Temporal schedule/alert route startup checks는 `docs/operations/trading-runtime-runbook.md`에 정리했다.
- Kafka와 Temporal activity 경계의 MDC trace propagation은 적용됐다. Temporal SDK interceptor, OpenTelemetry exporter wiring, 운영 대시보드 UI는 아직 남아 있다.
- 단발성 전략의 entry buy와 완전 체결 시 `COMPLETED` lifecycle은 execution-service로 들어왔고, `final_price_bating_v1_strategy_execution` 운영 테이블 migration도 추가됐다. sell policy는 추가 정리가 필요하다.
- daily execution schedule은 설정 기반 US trading calendar를 거쳐 실행된다. 주말과 설정된 휴장일은 active strategy 실행을 skip하며, stock-purchase-service 주문 guard는 설정 기반 조기폐장과 날짜별 조기폐장 시간 override를 적용한다. 휴장일/조기폐장 데이터 자동 동기화와 broker live-market acceptance 검증은 남아 있다.
- outbox 저장과 Kafka 발행은 분리되었고, publisher 실패 시 `nextAttemptAt` 기반 exponential backoff로 재시도한다. 여러 publisher 인스턴스가 동시에 실행될 때는 `PROCESSING` claim lease로 row 중복 발행을 줄인다. 운영 수준의 DB transaction boundary와 Kafka exactly-once 수준의 보장은 추가 hardening이 필요하다.

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

1. 완료: `StrategyExecutionStartRequested` proto/event 계약을 추가한다.
2. 완료: `stock-search-service`가 기존 저장 이벤트 대신 start request를 outbox로 발행한다.
3. 완료: `strategy-execution-service`에 start request consumer를 추가한다.
4. 부분 완료: 단발성 전략도 execution-service에서 entry buy intent를 만들고, fill event를 누적 기록해 완전 체결 시 `COMPLETED`로 닫을 수 있다. sell policy는 남아 있다.
5. 완료: `strategy-execution-service`가 첫 실행에서 `OrderIntentCreated`를 발행한다.
6. 완료: purchase-service가 broker order id를 저장하고 internal order intent submission과 매핑한다.
7. 완료: purchase-service가 `OrderSubmitted`, `OrderRejected`, `OrderCancelled`, `OrderFilled`, `OrderPartiallyFilled`를 발행한다.
8. 완료: strategy-execution-service가 주문/체결 이벤트를 수신해 기록하고, fill event로 전략 상태를 갱신한다.
9. 완료: 라오어 cycle close와 auto restart 정책을 명시적으로 저장한다.
10. 완료: active strategy state를 persistence adapter로 옮긴다.
11. 완료: Temporal schedule로 daily execution trigger를 붙인다.
12. 완료: stock-purchase reconciliation에 durable cursor, unmatched execution 저장, KIS wrapper 기반 주문/체결 조회 매핑, cursor 기준 rolling 날짜 범위 backfill, KIS 조회 cursor pagination, 조회성 호출 retry/backoff, wrapper 호출 rate-limit/circuit breaker를 추가한다. 미매칭 체결은 확정 fill로 저장하지 않아 늦게 생성/복구된 order intent submission과 재매칭될 수 있다.
13. 완료: 주문 lifecycle을 `SUBMISSION_UNKNOWN` 복구, `CANCEL_PENDING` 취소 요청 확정 대기, `OrderCancelled` 발행까지 확장하고, direct sell event 경로를 order intent lifecycle로 통일한다. 직접 sell order intent 제출은 market sell port 호출, submission 저장, `OrderSubmitted` 발행 fixture test로 고정한다.
14. 완료: 발행 측 outbox 적용 범위를 strategy-execution과 stock-purchase의 publisher까지 확장하고, publisher 실패 재시도에 `nextAttemptAt` 기반 exponential backoff와 `PROCESSING` claim lease를 적용한다.
15. 부분 완료: stock-purchase-service 내부 주문/조회 KIS 계약을 broker gateway anti-corruption layer로 분리한다. 주문 제출 transient 실패와 broker order id 누락은 `SUBMISSION_UNKNOWN` 복구 흐름으로 보내고, wrapper 호출 rate-limit/circuit breaker를 둔다. root wrapper의 국내 주문/주문체결조회/정정취소가능조회 HTTP 계약, 국내 주문체결조회 응답 정규화와 상태명/거절사유 alias 보존, stock-purchase의 해외 주문체결/잔고 top-level alias 매핑, 해외 주문 제출/취소/상태 복구 조회의 exchange 전파, token은 fixture test, real/mock scope별 만료 기반 cache, optional local-file persistence, optional JDBC shared token store, JDBC refresh TTL lock으로 보강했다. SUBMISSION_UNKNOWN/CANCEL_PENDING 복구의 주문 상태 조회는 설정 기반 날짜 window로 조회한다. 모의투자 query/submit/query/cancel smoke test 경계와 runbook은 추가했지만, 별도 broker wrapper service 배포, secret manager 연동, 실제 KIS 모의/실계좌 실행 증적은 남아 있다.
16. 완료(로컬 코드/테스트 기준): stock-purchase-service의 broker 제출 전 risk guard를 추가한다. 주문 단위/종목별 금액 한도, 활성 매수 주문 기준 계좌 pending exposure 한도, KIS 국내/해외 계좌 스냅샷 기반 계좌 exposure 한도, KIS 국내/해외 orderable cash 기반 현금 사용 한도, 하루 broker 제출 건수 한도, 중복 주문 kill switch, 전략 prefix enable allow-list/disable, order intent 및 전략 prefix 기반 mock/live broker route 검증은 적용됐다. KIS alias 기반 cash bucket 분리, 설정 기반 기준 통화 환산, HTTP FX provider 경계, root wrapper의 KIS overseas daily chart price 기반 FX provider도 추가됐다. production-like profile startup safety가 핵심 risk 한도, 계좌 cash guard, 중복 kill switch, 전략 allow-list, 종목별 한도, mock/live 정책 누락을 차단한다. KIS 통화쌍 symbol 설정의 실계좌 검증은 운영 검증으로 남아 있다.
17. 완료(로컬 코드/API/테스트 기준): 운영 관측성을 추가한다. 주문 제출 실패, `SUBMISSION_UNKNOWN` 지속, reconciliation 실패, 미매칭 broker execution은 log 기반 alert port, Micrometer counter, optional generic webhook, Slack incoming webhook, PagerDuty Events API v2로 노출하고, 알림 payload에 현재 trace context를 포함하며, 라오어와 최종가 베팅 전략별 현재 현금/보유/평단 조회 API와 strategy-execution 운영 상태 API를 추가했다. `stock-purchase-service`와 `strategy-execution-service`는 actuator health/metrics/prometheus endpoint와 outbox publisher 성공/실패 counter를 노출한다. Kafka inbound/outbound와 Temporal activity 경계의 MDC trace propagation은 테스트로 고정됐다. Temporal SDK interceptor, OpenTelemetry exporter wiring, 대시보드 UI는 운영 하드닝 잔여 작업으로 남아 있다.
18. 부분 완료: daily active strategy execution에 설정 기반 US trading calendar를 추가하고, stock-purchase-service broker 제출 전 설정 기반 주문 가능 시간/LOC/MOC 마감 guard를 추가한다. 주말과 설정 휴장일 skip, 설정 기반 주문 시간 guard, 공통 조기폐장 시간, 날짜별 조기폐장 시간 override는 적용됐고, strategy-execution order session date는 장마감 이후 다음 거래 가능일로 보정한다. 휴장일/조기폐장/마감 시간 데이터 자동 동기화와 broker live-market acceptance 검증은 남아 있다.
19. 부분 완료: 배포/설정/보안 가드를 추가한다. KIS secret 템플릿, runtime-injected KIS secret source 지원, production-like profile의 로컬 `application-secret.properties` property source 차단(root wrapper, stock-search-service, stock-purchase-service, strategy-execution-service), KIS token persistence type/duration/JDBC lock startup validation, stock-search/strategy Temporal schedule startup validation, stock-purchase operational alert route startup validation, Kubernetes/Vault-style secret mount 배포 템플릿, External Secrets Operator 기반 Vault SecretStore/ExternalSecret 템플릿, Vault read policy/Kubernetes auth role bootstrap 템플릿, MySQL/Kafka/Temporal bootstrap manifest, Kafka topic bootstrap Job, GHCR 이미지 빌드 workflow, GitHub Actions/GHCR delivery verification helper, 운영 산출물 검증 workflow, 운영 runbook, production-like profile startup validation, stock-search/strategy/final-price/purchase state 운영 DDL, MySQL 런타임 드라이버, 이미지 태그 렌더링/infra manifest/secret manifest/SQL migration ConfigMap/마이그레이션 Job/Deployment rollout helper, 배포 후 Kubernetes runtime verification helper는 적용했다. 실제 managed 운영 DB/Kafka/Temporal 클러스터 선택/적용, Vault/Kubernetes/GHCR 클러스터 적용 검증은 남아 있다.

### Broker Cancel Request Boundary

- `stock-purchase-service` now has a separate cancel-submission use case and KIS domestic/overseas `order-rvsecncl` broker adapter path.
- Operators can submit explicit cancel requests through `POST /orders/cancellations` in `stock-purchase-service`; this endpoint calls the cancel-submission use case and returns the broker cancel-request submission status.
- Domestic cancel submissions now call KIS `inquire-psbl-rvsecncl` first and reject the cancel request when the original order is missing from the cancelable list or the requested quantity exceeds the KIS possible quantity bucket, including `psbl_qty`, `ord_psbl_qty`, and revision/cancel possible quantity aliases. The cancelable lookup matches order identity through current order number, original order number, branch/order-org number, and product-code aliases, then selects the matching row with the highest possible quantity before submitting cancel.
- Overseas cancel submissions now query KIS `inquire-ccnl` first and reject the cancel request when the original order is missing, already rejected/cancelled, has no remaining quantity, or the requested cancel quantity exceeds the remaining quantity.
- Cancel submission success is treated as broker acceptance of the cancel request, not as final cancellation. Accepted or unclear cancel submissions mark the original order intent submission as `CANCEL_PENDING`, and recovery keeps polling broker status from that durable state.
- `OrderCancelled` remains emitted only after broker status lookup/recovery confirms the original order is cancelled. A still-submitted broker status keeps the row in `CANCEL_PENDING` without republishing `OrderSubmitted`.
- When `SUBMISSION_UNKNOWN` or `CANCEL_PENDING` status recovery observes broker `PARTIALLY_FILLED`/`FILLED` state, stock-purchase-service now subtracts already saved `execution_fill` quantity from the broker cumulative fill quantity and emits only the missing delta as a deterministic `OrderPartiallyFilled` or `OrderFilled` outbox event. `CANCEL_PENDING` partial-fill recovery keeps the submission in cancel-pending state and does not republish `OrderSubmitted`.
- Domestic and overseas status recovery treat KIS rejection reason aliases, including raw `rjct_rson_cd`/`RJCT_RSON_CD`, as a broker rejection signal when only the rejection code is present. If KIS pairs that code with an explicit non-rejection marker such as `Not rejected`, recovery ignores the marker/code combination so accepted or filled rows are not misclassified as rejected. The domestic wrapper preserves those fields in the daily execution order protobuf contract.
- Domestic KIS cancel requests require `KRX_FWDG_ORD_ORGNO` and `ORGN_ODNO`; submitted KIS `KRX_FWDG_ORD_ORGNO` values are persisted on `order_intent_submission.branchOrderNumber` and reused when a later domestic cancel request omits the branch/order-org number. If the stored branch/order-org number is missing, the domestic cancel path now lets `inquire-psbl-rvsecncl` resolve the branch from the cancelable row before submitting `order-rvsecncl`. KIS all-zero order/original-order/branch-order/execution-id placeholders such as `0000000000` are ignored during broker submit/history mapping so they are not treated as real broker order, branch, or execution ids. When broker id is still unknown, status recovery can use a KIS row with missing side only if the remaining symbol/date/quantity/price filters leave an unambiguous candidate; explicit opposite-side rows remain excluded. Overseas cancel requests use `OVRS_EXCG_CD`, `PDNO`, and `ORGN_ODNO`. Modify/revise orders and production credential/live-market verification are still production-hardening gaps.

### Order Trading Hours Guard

- `stock-purchase-service` now runs a config-backed trading-hours guard before broker submission through `OrderRiskControlPort`.
- The guard blocks order intents before KIS calls when the request is outside the configured domestic or US order window, lands on a configured market holiday, or exceeds the configured LOC/MOC cutoff.
- US early-close dates can be configured with a common early-close time, and individual dates can override that close time through `early-close-times[yyyy-MM-dd]`; this limits LIMIT, LOC, and MOC submission windows for those dates.
- `strategy-execution-service` production-like startup validation now rejects malformed US trading-calendar zone/date/time settings, early-close times outside the regular session, active execution schedule/calendar time-zone mismatches, and active execution schedule times outside the configured regular session before daily strategy scheduling can run with silent fallback values.
- `stock-purchase-service` production-like startup validation now rejects malformed domestic/US trading-hours zone/date/time settings, invalid LOC/MOC cutoffs, early-close/cutoff combinations that would leave no valid order window, and enabled operational alert routes that are blank, local, or missing PagerDuty routing metadata.
- The legacy `stock-purchase-service` scheduler also uses the configured overseas US trading calendar. Sell-order creation and simulation stay inside the configured order window, while submission recovery/reconciliation can keep running for the whole configured US trading date; all three paths skip US non-trading days.
- Rejected intents follow the existing risk rejection path: rejected submission storage plus `OrderRejected` publication.
- This is still not a full exchange-calendar integration. Automatic holiday/early-close synchronization and broker-verified live-market acceptance checks remain production hardening work.

### Trading Operations Status API

- `stock-purchase-service` exposes `GET /operations/trading/status` for operator dashboard polling.
- The endpoint reports order submission status counts, order execution outbox status counts, reconciliation cursor health, unmatched execution totals, and recent unmatched broker executions.
- This fills the API side of the operating dashboard need. A UI plus full OpenTelemetry exporter integration are still production hardening work.

### Strategy Execution Status API

- `strategy-execution-service` exposes `GET /strategy-executions/laor-v4` and `GET /strategy-executions/laor-v4/{executionId}` for Laor V4 operating state, including current progress round/T, available cash, holding quantity, average purchase price, cycle, and mode.
- `strategy-execution-service` exposes `GET /strategy-executions/final-price-bating-v1` and `GET /strategy-executions/final-price-bating-v1/{executionId}` for final-price bating operating state, including buy/sell fill quantities, remaining quantities, average fill prices, current cash, current holding quantity, and current average price.

### Kafka Trace Propagation

- `strategy-execution-service` and `stock-purchase-service` now persist `traceId`, `spanId`, and `traceParent` with outbox rows.
- Outbox Kafka publishers copy the stored trace values into Kafka `traceId`, `spanId`, and `traceparent` headers before sending the event.
- Kafka listeners restore those headers into MDC before invoking use cases, so downstream logs and operational alerts can keep the same trace context.
- Production DB migrations must add the outbox trace columns before deployment. The current manual MySQL migration is `docs/operations/sql/20260602_add_outbox_trace_columns.mysql.sql`.

### Temporal Trace Propagation

- Temporal workflow inputs now carry a `TemporalTraceContext`.
- If the caller did not provide one, workflow implementations derive a deterministic W3C-style trace context from the Temporal workflow type, workflow id, and run id.
- Temporal activities restore that trace context into MDC while invoking strategy use cases, so created outbox rows and downstream Kafka events can retain the Temporal execution trace.
- Full Temporal SDK interceptors, OpenTelemetry exporter wiring, and dashboard UI correlation remain production hardening work.

### Broker Account Snapshot API

- `stock-purchase-service` exposes `GET /operations/trading/account-snapshot` for broker-backed account position checks.
- Supported markets are `OVERSEAS_US` and `DOMESTIC`; the adapter calls the root KIS wrapper's overseas or domestic `/trading/inquire-balance` endpoint.
- The response reports domestic/overseas positions, optional KIS summary fields, legacy `availableCashAmount`, `cashCurrency`, and separated `orderableCashAmount`, `settledCashAmount`, and `withdrawableCashAmount` when KIS provides those aliases. Balance summary parsing accepts KIS `output2` as either an object or an array whose first object contains the summary buckets.
- Risk guard cash checks prefer `orderableCashAmount` and fall back to the legacy `availableCashAmount` only for compatibility with older account snapshot providers. Order notional, pending buy notional, broker exposure, and orderable cash are normalized to the configured risk base currency through an `FxRatePort`; the default provider uses static `rates-to-base`, an optional HTTP provider can fetch operator-managed live rates, and `provider=kis-wrapper` can call the root wrapper's KIS overseas daily chart price endpoint for configured currency pairs. Account exposure and daily order count checks always include the current order market even if an operator-supplied market list omits it. KIS symbol mapping verification and broader account cash/exposure modeling remain hardening work.

## Open Questions

- `autoRestart=true`일 때 execution-service가 즉시 다음 cycle을 여는 현재 정책으로 충분한가, 아니면 search-service의 재승인을 다시 받아야 하는가?
- 단발성 전략의 매도 정책과 완료 판정은 execution-service가 소유할 것인가, purchase-service의 기존 sell scheduler를 단계적으로 유지할 것인가?
- 체결 조회는 purchase-service scheduler와 durable cursor로 충분한가, broker webhook을 지원할 경우 webhook을 우선할 것인가?
- 해외 주문에서 모의투자와 실전투자의 주문 유형 차이를 strategy parameter로 노출할 것인가, adapter 정책으로 숨길 것인가?
- 내부 취소 intent를 별도 command로 모델링할 것인가, 현재처럼 broker 조회 결과 기반 `OrderCancelled`부터 유지할 것인가?
- outbox는 현재 각 서비스별 table로 구현했다. 공통 abstraction으로 묶을지는 후속 리팩터링에서 판단한다.
- daily execution에서 미국장 휴장일을 market-data adapter, broker calendar, 별도 trading-calendar service 중 어디에서 판단할 것인가?

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

Implementation note: `SUBMISSION_UNKNOWN` recovery now passes the stored order quantity and submitted price into broker status lookup. When broker order id is absent, stock-purchase-service first matches broker history by symbol, side, and submitted date, then narrows candidates by ordered quantity and submitted price when matching broker row fields are available. Recovery only resolves the broker status when that narrowed set has a single candidate; otherwise the submission remains `UNKNOWN` to avoid assigning another same-day order's terminal state.

KIS overseas order history mapping includes `ft_ord_unpr3`/`FT_ORD_UNPR3` as submitted order price aliases, so broker-order-id-free recovery can still use the submitted price to disambiguate matching rows.

Reconciliation note: unmatched broker executions are removed from `unmatched_execution` after the corresponding fill is successfully persisted or already exists, so the operations status API reports only still-unresolved broker executions. Broker execution rows are processed in deterministic `createdAt`/execution-id order before fill event publication, so out-of-order wrapper responses do not reorder partial/final fill progression. The reconciliation cursor records the last raw broker execution row observed even when a cumulative row has no new fill delta, keeping operations visibility aligned with broker lookup results instead of only newly saved fills.

이 구조에서는 모든 전략이 같은 출발점을 가진다. 전략마다 실행 기간만 다르다. 단발성 전략은 execution-service에서 짧게 종료되고, 라오어 같은 장기 전략은 같은 execution-service 안에서 여러 거래일 동안 상태를 이어간다. purchase-service는 전략을 모르는 주문/체결 서비스로 유지된다.
