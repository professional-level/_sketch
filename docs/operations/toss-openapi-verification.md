# Toss OpenAPI 연동 기록

기준일: 2026-07-08

## 코드 기준 매핑

현재 로컬 프록시 엔드포인트는 아래처럼 Toss 공식 문서 경로와 연결됩니다.

| 로컬 엔드포인트 | Toss OpenAPI | 비고 |
| --- | --- | --- |
| `POST /open-api/toss/token` | `POST /oauth2/token` | OAuth2 Client Credentials |
| `GET /open-api/toss/account` | `GET /api/v1/accounts` | `accountSeq` 확보용 |
| `GET /open-api/toss/stocks/snapshot` | `GET /api/v1/stocks` | 내부 이름은 snapshot 이지만 실제로는 종목 기본 정보 조회 |
| `GET /open-api/toss/stocks/balance` | `GET /api/v1/holdings` | `X-Tossinvest-Account` 필요 |
| `POST /open-api/toss/orders` | `POST /api/v1/orders` | BUY/SELL 모두 동일 경로, 방향은 payload 에 포함 |

## 로컬 설정 키

실제 비밀값은 `src/main/resources/application-secret.properties` 또는 환경변수로만 주입합니다.

- `akra.openapi.toss.client-id`
- `akra.openapi.toss.client-secret`
- `akra.openapi.toss.default-account-number` (선택, `GET /api/v1/accounts` 의 `accountSeq` 값)

토스 발급 화면의 `client_id`, `client_secret` 값은 우리 애플리케이션 설정 키인 `akra.openapi.toss.client-id`, `akra.openapi.toss.client-secret` 로 넣어야 합니다.
기본 경로는 이제 코드/설정에 반영되어 있어, 위 자격증명만 정상이면 계좌/종목/보유주식 조회까지 바로 호출 가능합니다.

## 공식 문서 기준 확인 사항

참고 문서:
- `https://developers.tossinvest.com/docs`
- `https://openapi.tossinvest.com/openapi-docs/overview.md`
- `https://openapi.tossinvest.com/openapi-docs/latest/api-reference/README.md`

확인 내용:
- Base URL: `https://openapi.tossinvest.com`
- 인증: OAuth2 Client Credentials
- 계좌/자산/주문 계열은 `Authorization: Bearer ...` 와 `X-Tossinvest-Account` 둘 다 필요
- 계좌 조회: `GET /api/v1/accounts`
- 보유 주식 조회: `GET /api/v1/holdings`
- 종목 기본 정보 조회: `GET /api/v1/stocks`
- 주문 생성: `POST /api/v1/orders`
- 공식 문서에는 별도 주문 simulation 엔드포인트가 없음

## 실제 연결 점검 결과

로컬 `application-secret.properties` 에서 Toss 발급값(`client_id`, `client_secret`)을 애플리케이션 설정 키(`akra.openapi.toss.client-id`, `akra.openapi.toss.client-secret`)로 정리한 뒤 직접 호출했습니다.

검증 결과:
- 외부 OAuth 토큰 발급: `POST https://openapi.tossinvest.com/oauth2/token` → `200 OK`
- 외부 계좌 조회: `GET /api/v1/accounts` → `200 OK`, 계좌 1건 확인
- 외부 종목 조회: `GET /api/v1/stocks?symbols=005930,AAPL` → `200 OK`, 종목 2건 확인
- 외부 보유주식 조회: `GET /api/v1/holdings` + `X-Tossinvest-Account` → `200 OK`, 보유 종목 5건 확인
- 로컬 프록시 토큰 발급: `POST /open-api/toss/token` → `200 OK`
- 로컬 프록시 계좌 조회: `GET /open-api/toss/account` → `200 OK`
- 로컬 프록시 종목 조회: `GET /open-api/toss/stocks/snapshot?symbols=005930,AAPL` → `200 OK`
- 로컬 프록시 보유주식 조회: `GET /open-api/toss/stocks/balance?accountNumber={accountSeq}` → `200 OK`

해석:
- Toss 자격증명은 현재 정상 동작합니다.
- 토큰 발급, 계좌 조회, 종목 조회, 보유주식 조회까지 외부 연동과 애플리케이션 프록시 경로가 모두 확인됐습니다.
- `default-account-number` 는 계좌 조회 결과의 `accountSeq` 를 넣으면 후속 계좌 API 호출을 더 단순하게 만들 수 있습니다.

## 후속 조치

1. 필요하면 `akra.openapi.toss.default-account-number` 에 확인된 `accountSeq` 반영
2. 주문 API는 실제 매매 전용이므로 별도 안전 계정/시장 조건에서만 검증

## 주의

- 주문 API는 실제 매매 동작이므로 이번 점검에서는 실행하지 않았습니다.
- `orders/simulations` 프록시는 공식 Toss 문서 기준 대응 엔드포인트가 없어 기본값을 비워둔 상태입니다.
