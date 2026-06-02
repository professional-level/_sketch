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
- `stock-purchase-service`는 broker 제출 결과가 불명확한 order intent를 `SUBMISSION_UNKNOWN`으로 저장하고, recovery use case에서 broker 상태를 다시 조회해 submitted/rejected/cancelled로 확정한다. 취소 요청은 accepted/unknown 시 원 주문 제출 row를 `CANCEL_PENDING`으로 보존하고, broker 조회가 실제 cancelled를 확인한 뒤에만 `OrderCancelled`를 발행한다.
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
- 내부 주문과 매칭되지 않는 broker execution은 `unmatched_execution` 저장소에 보존하고 운영 알림으로 노출한다.
- reconciliation 실패 시 cursor를 completed로 전진시키지 않고 failed 상태와 실패 사유를 기록한다.

현재 남은 것:

- `stock-purchase-service`의 체결 reconciliation은 cursor와 unmatched 저장, KIS wrapper 기반 주문/체결 조회 매핑, cursor 기준 날짜 범위 backfill, KIS 조회 cursor pagination을 갖췄다. KIS wrapper 조회성 호출에는 설정 기반 retry/backoff를 적용했고, wrapper 호출 전 rate-limit와 circuit breaker도 적용한다.
- legacy direct sell submission은 order intent lifecycle 경로로 통일되었다.
- `strategy-execution-service`의 `OrderIntentCreated` 발행과 `stock-purchase-service`의 주문/체결 이벤트 발행은 outbox 저장 후 scheduled publisher가 Kafka로 발행한다.
- stock-purchase-service의 주문/조회 KIS 원문 계약은 `adapter/out/broker`의 broker gateway anti-corruption layer로 격리되었다. 주문 제출 POST는 중복 주문을 피하기 위해 retry하지 않고 transient 실패나 broker order id 누락을 `SUBMISSION_UNKNOWN`으로 보낸다. KIS 주문 응답의 `rt_cd != 0`은 `msg_cd/msg1`을 보존한 broker rejection으로 분리해 `OrderRejected`와 rejected submission으로 확정한다. root wrapper는 KIS 표준 주문 응답 객체의 top-level `rt_cd`, `msg_cd`, `msg1`과 nested `output.ODNO`를 stock-purchase-service용 protobuf로 정규화한다. root wrapper의 국내 주문체결조회 protobuf 변환은 `output1` 배열/단일 객체/누락, `output2` 누락, 대문자 field alias를 허용하고 `ODNO`, `rjct_qty`, `cncl_yn`, `cncl_cfrm_qty`, `tot_ccld_qty`, `rmn_qty` 같은 reconciliation 핵심 필드를 fixture test로 고정한다. 해외 주문체결조회는 KIS `ODNO` 검색 불가 제약 때문에 일자/종목 범위로 조회한 뒤 응답 row의 broker order id를 client-side에서 매칭한다. 해외 주문체결조회와 잔고조회는 top-level `output`/`output1`/`output2`/cursor의 대소문자 alias를 허용한다. 해외 row 매핑은 `ODNO/odno`, `ORD_QTY/ft_ord_qty`, `TOT_CCLD_QTY/ft_ccld_qty`, `RMN_QTY/nccs_qty`, `AVG_PRVS/ft_ccld_unpr3` 같은 wrapper alias를 허용하고, 한글/영문 rejected/cancelled 상태명을 내부 주문 상태로 정규화한다. KIS wrapper 호출에는 local rate-limit와 circuit breaker가 있고, circuit open/rate-limit 초과처럼 broker에 닿지 않은 실패는 broker rejection 이벤트로 발행하지 않는다. root wrapper의 KIS token은 real/mock scope별 만료 기반 cache, optional local-file persistence, optional JDBC shared token store, JDBC refresh TTL lock으로 관리한다. 별도 broker wrapper service 배포와 secret manager 연동은 아직 남아 있다.
- `stock-purchase-service`는 broker 제출 전 risk guard로 주문 단위 금액 한도, 종목별 주문 금액 한도, 활성 매수 주문 기준 계좌 pending exposure 한도, KIS 해외 계좌 스냅샷 기반 계좌 exposure 한도, KIS 해외 계좌 스냅샷의 broker available cash 기반 현금 사용 한도, 하루 broker 제출 건수 한도, 동일 전략/종목/태그 중복 kill switch, 전략 prefix disable, 전략 prefix별 mock/live broker route 검증을 적용한다.
- `stock-purchase-service`는 broker gateway를 통해 KIS 해외주식 잔고조회(`inquire-balance`)를 호출하고, `/operations/trading/account-snapshot`에서 해외 포지션 수량, 평균매입가, 현재가, 매입금액, 평가금액, 평가손익, available cash를 조회할 수 있다.
- 국내 잔고, 엄밀한 settled cash 구분, 다통화/환율 반영, order intent 계약 수준의 trading environment 명시는 별도 계좌/현금 상태 모델과 이벤트 계약 확장이 필요하므로 아직 남아 있다.
- `stock-purchase-service`는 주문 제출 실패, `SUBMISSION_UNKNOWN` 지속, reconciliation 실패, 미매칭 broker execution을 `OperationalAlertPort`로 알리고, 기본 구현은 로그 기반 alert adapter, Micrometer operational alert counter, optional generic webhook, Slack incoming webhook, PagerDuty Events API v2 sink로 둔다. 운영 알림 payload는 MDC의 `traceId`/`spanId`/`traceparent`를 함께 전파한다.
- `strategy-execution-service`는 `/strategy-executions/laor-v4`와 `/strategy-executions/laor-v4/{executionId}`에서 전략별 T, 현금, 보유 수량, 평균단가, cycle, mode를 조회할 수 있다.
- `stock-purchase-service`와 `strategy-execution-service`는 production-like profile에서 로컬 broker/Temporal endpoint나 mock 주문 설정이 남아 있으면 startup validation으로 실패한다.
- root sketch app의 KIS secret key 이름은 `src/main/resources/application-secret.properties.example` 템플릿으로 문서화했고, classpath secret 파일 없이 런타임 환경변수/secret source로도 주입할 수 있다. 운영 재시작 절차는 `docs/operations/trading-runtime-runbook.md`에 정리했다.
- Kafka와 Temporal activity 경계의 MDC trace propagation은 적용됐다. Temporal SDK interceptor, OpenTelemetry exporter wiring, 운영 대시보드 UI는 아직 남아 있다.
- 단발성 전략의 entry buy는 execution-service로 들어왔지만, sell policy와 completion lifecycle은 추가 정리가 필요하다.
- daily execution schedule은 설정 기반 US trading calendar를 거쳐 실행된다. 주말과 설정된 휴장일은 active strategy 실행을 skip하며, 휴장일 데이터 자동 동기화와 조기폐장/LOC/MOC 마감 시간 정책은 남아 있다.
- outbox 저장과 Kafka 발행은 분리되었지만, 운영 수준의 transaction boundary와 publisher retry/backoff 정책은 추가 hardening이 필요하다.

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
4. 부분 완료: 단발성 전략도 execution-service에서 entry buy intent를 만들 수 있다. sell policy와 completion lifecycle은 남아 있다.
5. 완료: `strategy-execution-service`가 첫 실행에서 `OrderIntentCreated`를 발행한다.
6. 완료: purchase-service가 broker order id를 저장하고 internal order intent submission과 매핑한다.
7. 완료: purchase-service가 `OrderSubmitted`, `OrderRejected`, `OrderCancelled`, `OrderFilled`, `OrderPartiallyFilled`를 발행한다.
8. 완료: strategy-execution-service가 주문/체결 이벤트를 수신해 기록하고, fill event로 전략 상태를 갱신한다.
9. 완료: 라오어 cycle close와 auto restart 정책을 명시적으로 저장한다.
10. 완료: active strategy state를 persistence adapter로 옮긴다.
11. 완료: Temporal schedule로 daily execution trigger를 붙인다.
12. 완료: stock-purchase reconciliation에 durable cursor, unmatched execution 저장, KIS wrapper 기반 주문/체결 조회 매핑, cursor 기준 날짜 범위 backfill, KIS 조회 cursor pagination, 조회성 호출 retry/backoff, wrapper 호출 rate-limit/circuit breaker를 추가한다.
13. 완료: 주문 lifecycle을 `SUBMISSION_UNKNOWN` 복구, `CANCEL_PENDING` 취소 요청 확정 대기, `OrderCancelled` 발행까지 확장하고, direct sell event 경로를 order intent lifecycle로 통일한다.
14. 완료: 발행 측 outbox 적용 범위를 strategy-execution과 stock-purchase의 publisher까지 확장한다.
15. 부분 완료: stock-purchase-service 내부 주문/조회 KIS 계약을 broker gateway anti-corruption layer로 분리한다. 주문 제출 transient 실패와 broker order id 누락은 `SUBMISSION_UNKNOWN` 복구 흐름으로 보내고, wrapper 호출 rate-limit/circuit breaker를 둔다. root wrapper의 국내 주문체결조회 응답 정규화, stock-purchase의 해외 주문체결/잔고 top-level alias 매핑, token은 fixture test, real/mock scope별 만료 기반 cache, optional local-file persistence, optional JDBC shared token store, JDBC refresh TTL lock으로 보강했다. 모의투자 query/submit/query/cancel smoke test 경계와 runbook은 추가했지만, 별도 broker wrapper service 배포, secret manager 연동, 실제 KIS 모의/실계좌 실행 증적은 남아 있다.
16. 부분 완료: stock-purchase-service의 broker 제출 전 risk guard를 추가한다. 주문 단위/종목별 금액 한도, 활성 매수 주문 기준 계좌 pending exposure 한도, KIS 해외 계좌 스냅샷 기반 계좌 exposure 한도, KIS 해외 available cash 기반 현금 사용 한도, 하루 broker 제출 건수 한도, 중복 주문 kill switch, 전략 prefix disable, 전략 prefix별 mock/live broker route 검증은 적용됐다. 국내 잔고, 엄밀한 settled cash 구분, 다통화/환율 반영, order intent 계약 수준의 trading environment 명시는 남아 있다.
17. 부분 완료: 운영 관측성을 추가한다. 주문 제출 실패, `SUBMISSION_UNKNOWN` 지속, reconciliation 실패, 미매칭 broker execution은 log 기반 alert port, Micrometer counter, optional generic webhook, Slack incoming webhook, PagerDuty Events API v2로 노출하고, 알림 payload에 현재 trace context를 포함하며, 라오어 전략별 T/현금/보유/평단 조회 API를 추가했다. Kafka와 Temporal activity 경계의 MDC trace propagation은 적용됐고, Temporal SDK interceptor, OpenTelemetry exporter wiring, 대시보드 UI는 남아 있다.
18. 부분 완료: daily active strategy execution에 설정 기반 US trading calendar를 추가하고, stock-purchase-service broker 제출 전 설정 기반 주문 가능 시간/LOC/MOC 마감 guard를 추가한다. 주말과 설정 휴장일 skip, 설정 기반 주문 시간 guard는 적용됐고, 휴장일/조기폐장/마감 시간 데이터 자동 동기화와 per-date cutoff 정책은 남아 있다.
19. 부분 완료: 배포/설정/보안 가드를 추가한다. KIS secret 템플릿, runtime-injected KIS secret source 지원, 운영 runbook, production-like profile startup validation은 적용했고, secret manager/vault 배포 연동과 운영 DB/Kafka/Temporal 배포 자동화는 남아 있다.

### Broker Cancel Request Boundary

- `stock-purchase-service` now has a separate cancel-submission use case and KIS domestic/overseas `order-rvsecncl` broker adapter path.
- Operators can submit explicit cancel requests through `POST /orders/cancellations` in `stock-purchase-service`; this endpoint calls the cancel-submission use case and returns the broker cancel-request submission status.
- Domestic cancel submissions now call KIS `inquire-psbl-rvsecncl` first and reject the cancel request when the original order is missing from the cancelable list or the requested quantity exceeds `psbl_qty`.
- Overseas cancel submissions now query KIS `inquire-ccnl` first and reject the cancel request when the original order is missing, already rejected/cancelled, has no remaining quantity, or the requested cancel quantity exceeds the remaining quantity.
- Cancel submission success is treated as broker acceptance of the cancel request, not as final cancellation. Accepted or unclear cancel submissions mark the original order intent submission as `CANCEL_PENDING`, and recovery keeps polling broker status from that durable state.
- `OrderCancelled` remains emitted only after broker status lookup/recovery confirms the original order is cancelled. A still-submitted broker status keeps the row in `CANCEL_PENDING` without republishing `OrderSubmitted`.
- Domestic KIS cancel requests require `KRX_FWDG_ORD_ORGNO` and `ORGN_ODNO`; overseas cancel requests use `OVRS_EXCG_CD`, `PDNO`, and `ORGN_ODNO`. Modify/revise orders and production credential/live-market verification are still production-hardening gaps.

### Order Trading Hours Guard

- `stock-purchase-service` now runs a config-backed trading-hours guard before broker submission through `OrderRiskControlPort`.
- The guard blocks order intents before KIS calls when the request is outside the configured domestic or US order window, lands on a configured market holiday, or exceeds the configured LOC/MOC cutoff.
- US early-close dates can be configured with a common early-close time; this limits LIMIT, LOC, and MOC submission windows for those dates.
- Rejected intents follow the existing risk rejection path: rejected submission storage plus `OrderRejected` publication.
- This is still not a full exchange-calendar integration. Automatic holiday/early-close synchronization, per-date cutoff data, and broker-verified live-market acceptance checks remain production hardening work.

### Trading Operations Status API

- `stock-purchase-service` exposes `GET /operations/trading/status` for operator dashboard polling.
- The endpoint reports order submission status counts, reconciliation cursor health, unmatched execution totals, and recent unmatched broker executions.
- This fills the API side of the operating dashboard need. A UI plus full OpenTelemetry exporter integration are still production hardening work.

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
- The first supported market is `OVERSEAS_US`; the adapter calls the root KIS wrapper's `/open-api/overseas/trading/inquire-balance` endpoint.
- The response reports overseas positions, optional KIS summary fields, and broker-reported available cash when present. Domestic balances, strict settled-cash classification, multi-currency conversion, and broader account cash/exposure modeling remain hardening work.

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

이 구조에서는 모든 전략이 같은 출발점을 가진다. 전략마다 실행 기간만 다르다. 단발성 전략은 execution-service에서 짧게 종료되고, 라오어 같은 장기 전략은 같은 execution-service 안에서 여러 거래일 동안 상태를 이어간다. purchase-service는 전략을 모르는 주문/체결 서비스로 유지된다.
