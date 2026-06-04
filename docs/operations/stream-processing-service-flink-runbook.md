# Stream Processing Service Flink Runbook

## Scope

`stream-processing-service` runs the LAOR order lifecycle milestone detector as a stateful Flink application job.

The job consumes raw order execution topics and emits:

- `laor-order-milestone-detected`
- `laor-order-anomaly-detected`

`strategy-execution-service` consumes milestone events and signals the LAOR Temporal workflow.

## Deployment

Use the Flink Kubernetes Operator `FlinkDeployment` manifest:

```sh
kubectl apply -f docs/operations/kubernetes/stream-processing-service-flinkdeployment.yaml
```

Before applying it to a real cluster, replace `spec.image` with the built image that contains:

```text
/opt/flink/usrlib/stream-processing-service.jar
```

The application entry class is:

```text
com.example.streamprocessingservice.laor.OrderLifecycleJobKt
```

## State Policy

The job is stateful. Keep these paths on durable storage:

- checkpoints: `file:///flink-data/checkpoints`
- savepoints: `file:///flink-data/savepoints`
- Kubernetes HA metadata: `file:///flink-data/ha`

The deployment uses `upgradeMode: savepoint`, so code/image changes should preserve lifecycle aggregation state.

## Runtime Configuration

Important environment variables:

- `KAFKA_BOOTSTRAP_SERVERS`: Kafka brokers.
- `LAOR_FLINK_GROUP_ID`: stable consumer group for this job.
- `LAOR_FLINK_CHECKPOINT_INTERVAL_MS`: checkpoint interval.
- `LAOR_FLINK_CHECKPOINT_STORAGE_PATH`: checkpoint storage path used by the job code.
- `LAOR_FLINK_ALLOWED_LATENESS_SECONDS`: out-of-order event tolerance.
- `LAOR_FILL_TIMEOUT_MS`: submitted order fill timeout.
- `LAOR_FLINK_PROCESSED_EVENT_TTL_MS`: dedup state TTL.

## Upgrade Procedure

1. Build and publish the new stream-processing image.
2. Update `spec.image` in the `FlinkDeployment`.
3. Keep `spec.job.upgradeMode: savepoint`.
4. Apply the manifest.
5. Confirm the restored job is running and consuming from the expected committed offsets.
6. Verify milestone output with one known `OrderSubmitted` to `OrderFilled` sequence.

If a manual restore is required, set `spec.job.initialSavepointPath` to the savepoint `_metadata` path and re-apply the manifest.

## Failure Handling

- Duplicate source events are filtered in Flink state with TTL.
- Invalid order sequences are emitted to `laor-order-anomaly-detected`.
- `strategy-execution-service` owns listener retry and DLT behavior for milestone consumption.
- Temporal remains the owner of strategy state transitions; Flink only emits lifecycle milestones.
