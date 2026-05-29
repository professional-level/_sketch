# 아키텍처 위험 및 한계 보고서

작성일: 2026-05-30
대상 프로젝트: `_sketch`
범위: `common`, `stock-search-service`, `stock-purchase-service`, `stock-service`, gRPC 예제 모듈

## 1. 요약

이 프로젝트는 주식 거래 마이크로서비스를 헥사고날 아키텍처와 이벤트 기반 흐름으로 풀어보려는 스케치로 볼 수 있다. 큰 방향은 좋다. `domain`, `application`, `adapter`를 나누고, 외부 API, Kafka, persistence를 포트 뒤로 숨기려는 의도가 보인다.

하지만 현재 구조를 운영 가능한 거래 시스템 관점에서 보면 가장 큰 문제는 패키지 구조가 아니라 신뢰성 경계가 아직 설계되지 않았다는 점이다. 특히 다음 네 가지가 핵심 위험이다.

1. 전략 저장과 Kafka 이벤트 발행이 원자적으로 묶이지 않는다.
2. 이벤트 계약이 실제 주문 생성에 필요한 정보를 충분히 담지 못한다.
3. 주문, 체결, 매도 상태 전이가 도메인 규칙으로 강제되지 않는다.
4. 스케줄러와 AOP가 비즈니스 흐름의 중심이 되어 테스트, 재처리, 장애 복구가 어렵다.

현재 구조는 "검색 서비스가 후보를 만들고 구매 서비스가 이벤트를 받아 주문을 만든다"는 데모 흐름을 설명하기에는 충분하다. 그러나 실제 매매 시스템처럼 중복 이벤트, 부분 체결, 외부 API 실패, 프로세스 재시작, Kafka 재전송, 주문 취소, 보상 처리까지 고려해야 하는 환경에서는 바로 한계가 드러난다.

## 2. 현재 시스템 흐름

현재 의도된 핵심 흐름은 다음과 같다.

```text
외부 주식 API
  -> stock-search-service
     -> 거래량 상위 종목 조회
     -> 조건 필터링
     -> FinalPriceBatingStrategyV1 생성
     -> 전략 저장
     -> StrategyCreatedEvent 생성
     -> strategy-saved Kafka 발행
  -> stock-purchase-service
     -> strategy-saved Kafka 소비
     -> BuyingStockPurchaseCommand 변환
     -> 전략 타입별 handler 실행
     -> PurchaseOrder 생성 및 저장
     -> 스케줄러가 체결/매도 처리
```

주요 코드 위치:

- `stock-search-service/src/main/kotlin/com/example/stocksearchservice/application/scheduler/StockSearchScheduler.kt`
- `stock-search-service/src/main/kotlin/com/example/stocksearchservice/domain/strategy/FinalPriceBatingStrategyV1.kt`
- `stock-search-service/src/main/kotlin/com/example/stocksearchservice/application/event/CompleteEntityAspect.kt`
- `stock-search-service/src/main/kotlin/com/example/stocksearchservice/application/event/DomainEventDispatcher.kt`
- `stock-search-service/src/main/kotlin/com/example/stocksearchservice/adapter/out/kafka/KafkaMessageServiceAdapter.kt`
- `stock-purchase-service/src/main/kotlin/com/example/stockpurchaseservice/adapter/in/event/ExternalEventListenerAdapter.kt`
- `stock-purchase-service/src/main/kotlin/com/example/stockpurchaseservice/application/service/BuyingStockPurchaseService.kt`
- `stock-purchase-service/src/main/kotlin/com/example/stockpurchaseservice/application/service/strategy/StrategyHandler.kt`
- `stock-purchase-service/src/main/kotlin/com/example/stockpurchaseservice/application/scheduler/StockTradeScheduler.kt`

## 3. 주요 위험 목록

| 번호 | 위험 | 심각도 | 영향 |
| --- | --- | --- | --- |
| R1 | DB 저장과 Kafka 발행의 일관성 부재 | 매우 높음 | 저장된 전략이 주문으로 이어지지 않거나, 중복 주문이 발생할 수 있음 |
| R2 | 이벤트 계약 부족 | 매우 높음 | 구매 서비스가 의미 있는 주문을 만들 수 없음 |
| R3 | 주문 상태 머신 미완성 | 매우 높음 | 부분 체결, 실패, 재시도, 매도 전환을 안전하게 처리하기 어려움 |
| R4 | Kafka 소비 idempotency 부재 | 매우 높음 | 동일 이벤트 재처리 시 중복 주문 가능 |
| R5 | 스케줄러에 비즈니스 오케스트레이션 집중 | 높음 | 테스트, 재실행, 장애 복구, 수동 보정이 어려움 |
| R6 | AOP 기반 도메인 이벤트 생성 | 높음 | 이벤트 발생 시점과 비즈니스 의미가 불명확함 |
| R7 | coroutine/reactive와 blocking 코드 혼재 | 높음 | 부하 상황에서 스레드 고갈 또는 지연 전파 가능 |
| R8 | 서비스 경계 불명확 | 중간~높음 | 검색, 전략, 주문, 체결 책임이 뒤섞임 |
| R9 | 공통 모듈 결합도 증가 위험 | 중간 | 서비스 간 독립 배포와 진화가 어려워짐 |
| R10 | 운영 관측성 부족 | 중간 | 장애 원인 추적, 재처리, 감사가 어려움 |

## 4. 상세 분석

### R1. 전략 저장과 Kafka 이벤트 발행의 일관성 부재

현재 `stock-search-service`는 전략을 저장한 뒤 도메인 이벤트를 Kafka로 발행하려는 구조다. 문제는 저장과 발행이 하나의 신뢰성 경계 안에 있지 않다는 점이다.

현재 구조의 특징:

- `FinalPriceBatingStrategyV1RepositoryImpl.saveAll()`이 전략 DTO를 persistence port에 저장한다.
- `CompleteEntityAspect`가 저장 전후로 `EventSupportedEntity.complete()`와 `DomainEventDispatcher.dispatch()`를 호출한다.
- `DomainEventDispatcher`는 `CoroutineScope(Dispatchers.IO + SupervisorJob())`로 이벤트마다 비동기 발행을 수행한다.
- `KafkaMessageServiceAdapter`가 KafkaTemplate으로 protobuf 메시지를 발행한다.

이 방식은 다음 장애 상황에 취약하다.

```text
상황 A
1. 전략 DB 저장 성공
2. 프로세스 종료 또는 Kafka 일시 장애
3. 이벤트 발행 실패
4. 구매 서비스는 해당 전략을 영원히 모름
```

```text
상황 B
1. 전략 DB 저장 성공
2. Kafka 발행 성공 여부를 애플리케이션이 확실히 기록하지 못함
3. 재시도 또는 수동 복구 중 같은 이벤트 재발행
4. 구매 서비스가 같은 전략으로 중복 주문 생성
```

거래 시스템에서 이 문제는 치명적이다. 후보 저장과 주문 요청 이벤트는 반드시 재처리 가능해야 하고, 누락과 중복을 모두 제어해야 한다.

권장 방향:

- Transactional Outbox 패턴을 도입한다.
- 전략 저장 트랜잭션 안에서 `outbox_event` 레코드를 함께 저장한다.
- 별도 publisher가 outbox를 읽어 Kafka로 발행한다.
- 발행 성공 시 outbox 상태를 `PUBLISHED`로 변경한다.
- Kafka message key는 `strategyId` 또는 `eventId`로 고정한다.
- outbox에는 `event_id`, `aggregate_id`, `event_type`, `payload`, `status`, `retry_count`, `created_at`, `published_at`을 둔다.

권장 흐름:

```text
UseCase
  -> Strategy aggregate 생성
  -> StrategyRepository.save(strategy)
  -> OutboxRepository.save(event)
  -> transaction commit

OutboxPublisher
  -> unpublished event 조회
  -> Kafka publish
  -> published 처리
```

### R2. 이벤트 계약이 실제 주문 생성에 부족함

현재 protobuf 계약은 `common/src/main/proto/event.proto`의 `StrategySavedEvent`다.

현재 필드:

```text
stock_id
stock_name
saved_at
type
meta
```

그런데 `stock-purchase-service`에서 주문을 만들려면 최소한 다음 정보가 필요하다.

- 전략 식별자
- 이벤트 식별자
- 매수 기준 가격
- 주문 기준 가격 또는 가격 산정 방식
- 예산
- 수량 산정 방식
- 전략 파라미터
- 생성 기준 시각
- 중복 처리 키
- 전략 버전

현재 구매 서비스의 `ExternalEventListenerAdapter.toCommand()`는 `purchasePrice = 0.0`으로 `BuyingStockPurchaseCommand.OfFinalPriceBatingV1`을 만든다. 이 값은 도메인적으로 의미 있는 주문 가격이 아니며, 실제 주문 생성의 근거가 이벤트에 없다는 신호다.

이벤트 이름도 모호하다. `StrategySavedEvent`는 "전략이 저장되었다"는 사실 이벤트에 가깝다. 하지만 구매 서비스는 이 이벤트를 "주문을 생성하라"는 명령처럼 소비한다. 사실 이벤트와 명령이 섞이면 서비스 간 결합이 커지고 재처리 의미가 흐려진다.

권장 방향:

- 이벤트를 목적별로 분리한다.

예시:

```text
StockCandidateDetected
  검색 서비스가 종목 후보를 찾았다는 사실

TradingStrategyCreated
  전략 서비스가 특정 전략 인스턴스를 만들었다는 사실

OrderRequested
  주문 서비스가 반드시 주문 생성을 시도해야 하는 명령성 메시지
```

또는 현재 서비스 수를 유지한다면 최소한 `StrategySavedEvent`에 다음을 추가해야 한다.

```text
event_id
strategy_id
strategy_version
stock_id
stock_name
strategy_type
decision_price
target_buy_price
budget
quantity_policy
created_at
idempotency_key
```

### R3. 주문 상태 머신이 도메인 규칙으로 강제되지 않음

`stock-purchase-service`의 주문 도메인은 `PurchaseOrder`, `SellingOrder`, `OrderState`를 갖고 있다. 그러나 상태 전이가 아직 안전하게 모델링되지 않았다.

현재 위험 지점:

- `OrderState`는 enum이지만 유효 전이를 강제하지 않는다.
- `changeOrderState()`가 있지만 실제 상태 변경이 보장되지 않는다.
- `project()` 내부에서 `copy()`를 호출하지만 반환값을 사용하지 않아 상태가 변하지 않는 코드가 있다.
- `FinalPriceBatingV1.createSellingOrder()`에는 `orderState = TODO()`가 남아 있다.
- 부분 체결, 전량 체결, 취소, 실패, 만료, 재주문이 모델에 없다.
- `StockTradeScheduler`가 주문 상태와 체결 상태를 직접 조합한다.

거래 도메인에서는 상태 전이가 시스템의 핵심이다. 다음과 같은 전이가 명시적으로 표현되어야 한다.

```text
REQUESTED
  -> SUBMITTING
  -> SUBMITTED
  -> PARTIALLY_FILLED
  -> FILLED
  -> SELL_REQUESTED
  -> SELL_SUBMITTED
  -> CLOSED

실패 계열:
SUBMITTING -> SUBMIT_FAILED
SUBMITTED -> CANCEL_REQUESTED -> CANCELLED
SUBMITTED -> EXPIRED
```

권장 방향:

- 주문 aggregate를 하나로 두고 side, phase, fill state를 분리한다.
- 외부 주문 ID, 내부 주문 ID, 체결 목록을 aggregate에 포함한다.
- 상태 전이는 메서드로만 가능하게 한다.
- 잘못된 전이는 예외 또는 실패 결과로 막는다.
- 부분 체결은 `Fill` 값 객체로 누적한다.

예시 모델:

```text
Order
  id
  strategyId
  stock
  side
  requestedPrice
  requestedQuantity
  filledQuantity
  externalOrderId
  state
  fills

Order.submit(externalOrderId)
Order.applyFill(fill)
Order.cancel()
Order.fail(reason)
Order.requestSell(policy)
```

### R4. Kafka 소비 idempotency 부재

Kafka consumer는 기본적으로 at-least-once 처리로 보는 것이 안전하다. 같은 메시지가 두 번 들어올 수 있고, consumer 재시작 후 마지막 offset 처리 경계에서 중복 처리가 발생할 수 있다.

현재 `ExternalEventListenerAdapter.createdStrategies()`는 메시지를 parse한 뒤 바로 `buyingStockPurchaseUseCase.execute()`를 호출한다. 이미 처리한 이벤트인지 확인하는 저장소가 없다. 따라서 같은 `strategy-saved` 메시지가 두 번 소비되면 주문도 두 번 만들어질 수 있다.

권장 방향:

- `processed_event` 테이블을 둔다.
- `event_id` 또는 `idempotency_key`에 unique constraint를 건다.
- 이벤트 처리 시작 시 insert를 시도하고, 이미 존재하면 skip한다.
- 주문 생성도 `strategy_id` 기준 unique constraint를 둔다.

예시:

```text
ProcessStrategySavedUseCase
  -> processedEventRepository.tryStart(eventId)
     -> false면 return
  -> orderRepository.existsByStrategyId(strategyId)
     -> true면 processed 처리 후 return
  -> order 생성
  -> processedEventRepository.markSuccess(eventId)
```

### R5. 스케줄러가 비즈니스 유스케이스 역할을 함

`StockSearchScheduler`와 `StockTradeScheduler`는 단순 트리거가 아니라 실제 비즈니스 절차를 직접 수행한다.

예를 들어 `StockSearchScheduler.finalPriceBatingStrategy1()`는 다음을 모두 수행한다.

- 현재 시간 계산
- 거래량 상위 종목 조회
- 도메인 analyzer 호출
- 프로그램 순매수 거래량 조회
- 필터링
- 전략 저장
- 이벤트 발행 흐름 유발

`StockTradeScheduler`도 다음을 수행한다.

- 미완료 주문 조회
- 전략별 매도 주문 생성
- 외부 매도 API 호출
- 체결 목록 조회
- in-memory queue로 중복 체결 필터링
- 구매 완료 simulation

이 구조는 다음 문제가 있다.

- 같은 흐름을 API, CLI, 재처리 job에서 재사용하기 어렵다.
- 테스트가 스케줄러 중심으로 커진다.
- 장애 후 특정 날짜/특정 종목만 재처리하기 어렵다.
- 트리거 정책과 비즈니스 정책이 결합된다.

권장 방향:

스케줄러는 얇게 유지한다.

```kotlin
@Scheduled(...)
suspend fun run() {
    discoverFinalPriceBatingCandidatesUseCase.execute(command)
}
```

실제 흐름은 application service로 이동한다.

```text
DiscoverFinalPriceBatingCandidatesUseCase
ReconcileExecutionsUseCase
CreatePurchaseOrderFromStrategyUseCase
CreateSellOrdersByStrategyUseCase
SimulatePurchaseFillUseCase
```

### R6. AOP 기반 도메인 이벤트 생성의 의미가 불명확함

현재 `CompleteEntityAspect`는 repository save/saveAll 주변에서 entity의 `complete()`를 호출하고 이벤트를 발행한다.

이 구조의 문제:

- 이벤트가 비즈니스 행위에서 발생하지 않고 저장 행위에 의해 발생한다.
- `complete()`라는 이름이 무엇을 의미하는지 도메인 언어로 명확하지 않다.
- 저장 전 `complete()`에서 이벤트를 만들고 저장 후 dispatch한다.
- save 실패, 부분 성공, saveAll 내부 실패 시 이벤트 상태 추론이 어렵다.
- AOP가 suspend 함수, proxy, transaction 경계와 얽히면 동작을 추론하기 어렵다.

도메인 이벤트는 "무슨 일이 일어났는가"를 나타내야 한다. 예를 들어 `StrategyCreated`, `OrderSubmitted`, `PurchaseFilled`처럼 비즈니스 행위 메서드에서 명시적으로 등록되는 편이 좋다.

권장 방향:

```text
val strategy = Strategy.create(...)
strategy.markSelected(...)
strategyRepository.save(strategy)
outboxRepository.saveAll(strategy.pullEvents())
```

또는 application service에서 명시적으로 이벤트를 구성한다.

```text
strategyRepository.save(strategy)
outboxRepository.save(TradingStrategyCreated.from(strategy))
```

AOP는 도메인 이벤트 생성보다 트랜잭션 로깅, 메트릭, auditing 같은 기술 관심사에 쓰는 편이 안전하다.

### R7. coroutine/reactive와 blocking 코드 혼재

프로젝트는 Kotlin suspend, WebFlux, reactive repository를 쓰려는 방향이다. 그러나 외부 API handler에는 WebClient `.block()` 호출이 존재한다.

위험:

- suspend 함수 안에서 blocking 호출이 섞일 수 있다.
- WebFlux event loop 또는 제한된 worker thread가 막힐 수 있다.
- 장애 시 timeout이 전파되지 않고 스케줄러가 지연될 수 있다.
- reactive, coroutine, blocking 스타일이 섞여 테스트와 성능 추론이 어렵다.

권장 방향:

- WebClient는 `awaitBody`, `awaitEntity` 등 coroutine adapter를 사용한다.
- 불가피한 blocking API는 별도 dispatcher로 격리한다.
- port 시그니처를 일관되게 `suspend` 기반으로 맞춘다.
- timeout, retry, circuit breaker 정책을 adapter에 둔다.

### R8. 서비스 경계가 아직 모호함

`stock-search-service`는 이름상 검색 서비스지만 실제로는 전략 생성과 이벤트 발행까지 담당한다. `stock-purchase-service`는 구매 서비스지만 매도 전략 실행, 체결 확인, 상태 보정까지 담당한다.

현재 서비스 이름과 책임:

```text
stock-search-service
  - 외부 주식 데이터 조회
  - 후보 종목 분석
  - 전략 생성
  - 전략 저장
  - Kafka 발행

stock-purchase-service
  - 전략 이벤트 소비
  - 주문 생성
  - 주문 저장
  - 외부 주문 API 호출
  - 체결 조회
  - 매도 주문 생성
```

서비스 수를 늘리는 것이 항상 답은 아니다. 하지만 bounded context는 분명해야 한다.

선택지 A: 현재 모듈 수 유지

- `stock-search-service`를 "strategy-discovery-service" 성격으로 명확히 한다.
- "검색"이 아니라 "전략 후보 생성"까지 책임진다고 문서화한다.
- 구매 서비스는 주문과 체결 lifecycle만 책임진다.

선택지 B: 책임 분리

```text
market-data-service
  외부 API 조회, 캐싱, normalization

strategy-service
  후보 분석, 전략 생성, 전략 이벤트 발행

order-service
  주문 생성, 외부 broker API 연동, 상태 관리

execution-service 또는 reconciler
  체결 조회, fill 반영, 보상 처리
```

스케치 단계에서는 선택지 A가 현실적이다. 대신 각 서비스의 책임 문장을 명확히 고정해야 한다.

### R9. 공통 모듈 결합도 증가 위험

`common`에는 annotation, event abstraction, topic, proto utility가 들어 있다. 공통 모듈은 편리하지만, 비즈니스 개념이 들어가기 시작하면 모든 서비스가 같은 변경 주기에 묶인다.

현재 위험:

- `common`의 event abstraction이 여러 서비스 도메인에 직접 영향을 준다.
- Kafka topic과 protobuf event가 공통 모듈에 있다.
- root `src/main/proto`와 `common/src/main/proto`에 유사 proto가 중복된다.
- 여러 서비스가 같은 enum과 event model에 강하게 묶일 가능성이 있다.

권장 방향:

- `common`은 기술적 공통 요소로 제한한다.
- 이벤트 계약은 별도 `contract` 또는 `api-contracts` 성격으로 관리한다.
- 서비스 내부 도메인 이벤트와 외부 integration event를 분리한다.
- protobuf는 owning context를 명확히 하고 중복 정의를 제거한다.

### R10. 외부 API 호출과 주문 저장의 saga 경계 부재

실제 주문은 외부 broker API와 내부 DB가 함께 움직인다. 이 둘은 하나의 DB 트랜잭션으로 묶을 수 없다.

현재 위험:

- 내부 주문 저장 성공 후 외부 주문 API 실패
- 외부 주문 API 성공 후 내부 DB 저장 실패
- 외부 주문 ID 저장 실패
- timeout이지만 실제 주문은 접수된 상황
- 같은 주문을 재시도하면서 중복 주문 접수

이 문제는 단순 transaction으로 해결되지 않는다. 주문 처리에는 saga 또는 process manager 개념이 필요하다.

권장 방향:

```text
OrderRequested
  -> 내부 Order 생성: REQUESTED
  -> SubmitOrderUseCase 실행
  -> 외부 API 호출 전 idempotency key 생성
  -> 외부 API 성공: SUBMITTED + externalOrderId 저장
  -> 외부 API 실패: SUBMIT_FAILED
  -> timeout: SUBMISSION_UNKNOWN
  -> reconciler가 외부 주문 조회로 상태 보정
```

`SUBMISSION_UNKNOWN` 상태가 중요하다. 금융/거래 시스템에서는 timeout을 실패로 단정하면 위험하다. 외부 시스템에서는 접수되었는데 내부 시스템만 모를 수 있기 때문이다.

### R11. 체결 처리 모델이 부족함

현재 `StockTradeScheduler.executionCheck()`는 하루 체결 목록을 조회하고 in-memory `executionQueue`로 중복을 거르려 한다. 이 방식은 프로세스 재시작에 취약하다.

위험:

- 애플리케이션 재시작 시 중복 체결 처리가 가능하다.
- 체결 이벤트가 부분 체결인지 전량 체결인지 명확하지 않다.
- 같은 종목, 같은 수량의 다른 주문을 구분하기 어렵다.
- 외부 주문 ID 기반 매핑이 불완전하다.
- 체결 반영과 매도 주문 생성이 같은 스케줄러 안에서 뒤섞인다.

권장 방향:

- 체결은 `Fill` 또는 `Execution` aggregate/value object로 저장한다.
- `externalExecutionId`에 unique constraint를 둔다.
- 주문 aggregate가 fill을 적용해 `filledQuantity`, `avgFillPrice`, `state`를 계산한다.
- 체결 reconciliation은 별도 use case로 둔다.

```text
ReconcileExecutionsUseCase
  -> 외부 API에서 execution list 조회
  -> externalExecutionId 기준 dedupe
  -> order.applyFill(fill)
  -> order state 저장
  -> 필요한 후속 이벤트 outbox 저장
```

### R12. ArchUnit 규칙은 있으나 보호력이 충분하지 않음

각 서비스에 ArchUnit 테스트가 있다. 방향은 좋다. 다만 현재 규칙은 실제 운영 품질을 지키기에는 부족하다.

위험:

- 일부 규칙에 `allowEmptyShould(true)`가 있어 패키지가 비어 있거나 대상 클래스가 없어도 통과한다.
- adapters가 domain에 접근하지 못하게 하는 규칙은 지나치게 강하거나 해석이 애매할 수 있다. inbound adapter가 application DTO만 쓰는 구조라면 가능하지만, 현실적으로 mapping 계층이 필요하다.
- application service가 scheduler에 묶여 있는 구조는 ArchUnit만으로 잡기 어렵다.
- coroutine blocking, transaction boundary, outbox 누락 같은 핵심 위험은 ArchUnit으로 검출되지 않는다.

권장 방향:

- 패키지별 최소 클래스 존재 규칙을 추가한다.
- `domain`이 Spring, WebClient, Kafka, persistence 타입에 의존하지 않는 규칙을 강화한다.
- application service가 adapter 구현체에 의존하지 않는 규칙은 유지한다.
- architecture fitness function을 추가한다.

예시:

```text
domain must not depend on Spring
domain must not depend on kotlinx.coroutines reactor/webflux
application must not depend on adapter
adapter/out/persistence entities must not leak to application
scheduler must only call use case interfaces
```

### R13. 문서화와 코드 주석 품질 문제

일부 주석은 인코딩이 깨져 읽기 어렵다. 스케치 프로젝트라도 도메인 의사결정과 TODO가 깨진 문자로 남아 있으면 다음 설계 판단의 근거가 사라진다.

위험:

- TODO의 의도를 파악하기 어렵다.
- 비즈니스 규칙과 임시 구현의 구분이 흐려진다.
- 설계 변경 시 과거 판단을 복구하기 어렵다.

권장 방향:

- 깨진 주석은 복구하거나 제거한다.
- 중요한 설계 판단은 `docs/adr/`에 ADR로 남긴다.
- 코드 주석은 "왜 이렇게 했는가" 중심으로만 남긴다.
- TODO는 issue 또는 명시적 backlog로 옮긴다.

## 5. 가장 먼저 정해야 할 설계 질문

아래 질문에 답하지 않으면 구현을 늘릴수록 구조가 더 흔들릴 가능성이 높다.

1. `stock-search-service`가 만든 것은 후보인가, 전략인가, 주문 요청인가?
2. `StrategySavedEvent`는 사실 이벤트인가, 명령 메시지인가?
3. 하나의 전략은 하나의 주문만 만들 수 있는가?
4. 같은 종목에 여러 전략이 동시에 걸릴 수 있는가?
5. 주문 idempotency key는 무엇인가?
6. 외부 주문 API timeout은 실패인가, 미확인 상태인가?
7. 부분 체결 후 즉시 매도 전략을 실행할 것인가, 전량 체결 후 실행할 것인가?
8. 체결 조회는 polling인가, event stream인가?
9. 전략 파라미터는 이벤트에 스냅샷으로 담을 것인가, 구매 서비스가 다시 조회할 것인가?
10. 서비스별 DB를 분리할 것인가, 스케치 단계에서는 공유 DB를 허용할 것인가?

## 6. 권장 목표 아키텍처

스케치의 방향을 유지하면서 안정성을 올리려면 다음 구조가 적절하다.

```text
stock-search-service
  adapter/in/scheduler
    FinalPriceBatingDiscoveryScheduler

  application/port/in
    DiscoverFinalPriceBatingCandidatesUseCase

  application/service
    DiscoverFinalPriceBatingCandidatesService

  domain
    StockCandidate
    TradingStrategy
    FinalPriceBatingPolicy

  application/port/out
    MarketDataPort
    StrategyRepositoryPort
    OutboxPort

  adapter/out/api
    MarketDataApiAdapter

  adapter/out/persistence
    StrategyPersistenceAdapter
    OutboxPersistenceAdapter

  adapter/out/kafka
    OutboxKafkaPublisher
```

```text
stock-purchase-service
  adapter/in/event
    StrategyEventListener

  application/port/in
    ProcessStrategyCreatedUseCase
    SubmitOrderUseCase
    ReconcileExecutionsUseCase

  application/service
    ProcessStrategyCreatedService
    SubmitOrderService
    ReconcileExecutionsService

  domain
    Order
    Fill
    OrderStateMachine
    OrderPolicy

  application/port/out
    OrderRepositoryPort
    ProcessedEventPort
    BrokerOrderPort
    OutboxPort

  adapter/out/api
    BrokerOrderApiAdapter

  adapter/out/persistence
    OrderPersistenceAdapter
    ProcessedEventPersistenceAdapter
    OutboxPersistenceAdapter
```

핵심 원칙:

- 스케줄러와 Kafka listener는 얇게 둔다.
- use case가 트랜잭션과 비즈니스 흐름을 조정한다.
- 도메인은 상태 전이와 비즈니스 불변식을 강제한다.
- 외부 이벤트 발행은 outbox로 보장한다.
- 이벤트 소비는 processed event store로 멱등 처리한다.

## 7. 단계별 개선 로드맵

### Phase 1. 계약 정리

목표: 이벤트 의미와 주문 생성 입력을 명확히 한다.

작업:

- `StrategySavedEvent`의 의미를 재정의한다.
- `TradingStrategyCreated`와 `OrderRequested`를 분리할지 결정한다.
- 이벤트에 `event_id`, `strategy_id`, `idempotency_key`, `strategy_version`을 추가한다.
- 구매 서비스에서 `purchasePrice = 0.0` 임시 매핑을 제거한다.

완료 기준:

- 이벤트 하나만 봐도 구매 서비스가 주문 생성 여부와 기준을 판단할 수 있다.
- 같은 이벤트를 두 번 받아도 같은 주문으로 귀결될 수 있다.

### Phase 2. Outbox와 idempotency 도입

목표: 이벤트 누락과 중복 주문을 막는다.

작업:

- `stock-search-service`에 outbox table을 추가한다.
- 전략 저장과 outbox 저장을 같은 transaction으로 묶는다.
- outbox publisher를 추가한다.
- `stock-purchase-service`에 processed event table을 추가한다.
- 주문 테이블에 `strategy_id` unique constraint를 둔다.

완료 기준:

- Kafka 장애 중에도 전략 이벤트가 유실되지 않는다.
- 같은 Kafka 메시지를 여러 번 소비해도 주문은 한 번만 생성된다.

### Phase 3. 주문 상태 머신 재설계

목표: 주문/체결/매도 전이를 도메인에서 강제한다.

작업:

- `OrderState`를 실제 거래 lifecycle 기준으로 재정의한다.
- `Order.applyFill()`, `Order.submit()`, `Order.failSubmit()`, `Order.cancel()` 등을 도입한다.
- 부분 체결과 외부 주문 ID를 모델링한다.
- `copy()`만 호출하고 결과를 버리는 상태 전이 코드를 제거한다.

완료 기준:

- 잘못된 상태 전이는 테스트에서 실패한다.
- 부분 체결, 전량 체결, 취소, 실패 시나리오를 단위 테스트로 검증한다.

### Phase 4. 스케줄러 로직 유스케이스 이동

목표: 트리거와 비즈니스 흐름을 분리한다.

작업:

- `StockSearchScheduler.finalPriceBatingStrategy1()` 내용을 application service로 이동한다.
- `StockTradeScheduler.executionCheck()`를 `ReconcileExecutionsUseCase`로 이동한다.
- `simulateStockPurchase()`를 테스트 fixture 또는 별도 simulation use case로 분리한다.

완료 기준:

- 스케줄러는 use case 호출만 한다.
- 같은 use case를 테스트, 수동 재처리, API에서 재사용할 수 있다.

### Phase 5. 외부 API resilience 정리

목표: timeout, retry, unknown state를 제어한다.

작업:

- WebClient `.block()` 제거 또는 blocking 격리.
- API timeout 정책 명시.
- retry 가능한 오류와 불가능한 오류 분리.
- 주문 제출 timeout 시 `SUBMISSION_UNKNOWN` 상태 도입.
- reconciler가 외부 주문 조회로 상태를 보정한다.

완료 기준:

- 외부 API 장애가 내부 thread pool 고갈로 번지지 않는다.
- timeout 후 중복 주문을 내지 않는다.

## 8. 단기 수정 우선순위

짧은 시간 안에 리스크를 크게 낮추려면 다음 순서가 좋다.

1. `StrategySavedEvent`에 `strategy_id`, `event_id`, `idempotency_key`, 가격/예산 정보를 추가한다.
2. `stock-purchase-service`에서 processed event 저장소를 추가한다.
3. 주문 저장 시 `strategy_id` unique constraint를 추가한다.
4. `purchasePrice = 0.0` 임시 매핑을 제거한다.
5. `StockSearchScheduler`의 전략 생성 로직을 use case로 이동한다.
6. `StockTradeScheduler`의 체결 처리 로직을 use case로 이동한다.
7. `CompleteEntityAspect` 기반 이벤트 발행을 outbox 기반으로 대체한다.
8. WebClient `.block()` 호출을 coroutine 방식으로 교체한다.
9. 주문 상태 전이 단위 테스트를 먼저 작성한다.
10. 깨진 주석과 중복 proto/settings 항목을 정리한다.

## 9. 결론

현재 프로젝트는 "헥사고날 + 이벤트 기반 주식 거래 시스템"의 방향을 잡는 데는 의미가 있다. 그러나 지금 가장 중요한 병목은 패키지 구조가 아니라 거래 흐름의 신뢰성이다.

운영 가능한 구조로 가려면 다음 세 가지를 먼저 고정해야 한다.

```text
1. 이벤트는 유실되지 않아야 한다.
2. 이벤트는 중복 처리되어도 결과가 같아야 한다.
3. 주문 상태 전이는 도메인 규칙으로 강제되어야 한다.
```

이 세 가지가 정리되면 나머지 헥사고날 구조, adapter 분리, protobuf 계약, scheduler 분리는 자연스럽게 안정화된다. 반대로 이 세 가지 없이 모듈만 더 나누면 서비스 수는 늘지만 장애 지점과 운영 복잡도만 커질 가능성이 높다.
