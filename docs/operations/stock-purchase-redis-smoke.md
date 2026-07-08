# Stock Purchase Redis Smoke

This smoke verifies the optional Redis fast path for `stock-purchase-service`.
Redis is not a source of truth. The database-backed `processed_event` table
remains authoritative; Redis only short-circuits duplicates and rate-limits
broker gateway calls when the `redis` profile is enabled.

## Configuration

Enable the profile explicitly:

```powershell
$env:SPRING_PROFILES_ACTIVE="redis,kafka"
```

The Redis profile reads `stock-purchase-service/src/main/resources/application-redis.yml`:

```yaml
akra:
  redis:
    key-prefix: akra:stock-purchase
    processed-event:
      enabled: true
      lease-ttl: 5m
      completed-ttl: 7d
    broker-rate-limit:
      enabled: true
      capacity: 5
      refill-per-second: 5
      bucket-ttl: 10s
      fail-open: false
```

## Local Smoke

1. Start Redis on `127.0.0.1:6379`.
2. Use Java 17:

```powershell
$env:JAVA_HOME="C:\Users\dorani\.jdks\azul-17.0.9"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

3. Run the stock-purchase tests:

```powershell
.\gradlew.bat :stock-purchase-service:test
```

4. Start the service with Redis enabled:

```powershell
.\gradlew.bat :stock-purchase-service:bootRun --args="--spring.profiles.active=redis,kafka"
```

5. Confirm startup creates Redis-backed beans:

- `RedisBackedProcessedEventAdapter`
- `RedisProcessedEventFastPathAdapter`
- `RateLimitedBrokerGateway`
- `RedisBrokerGatewayRateLimiter`

## Expected Behavior

- If Redis is unavailable, processed-event acquisition returns `UNAVAILABLE`
  and falls back to the database-backed adapter.
- Broker gateway rate limiting fails closed by default.
- Set `akra.redis.broker-rate-limit.fail-open=true` only for controlled local
  diagnostics where broker API availability matters more than rate protection.
- Do not clear `processed_event` database rows during replay unless duplicate
  order impact is understood.
