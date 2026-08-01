# smartlife Monitoring

This monitoring stack runs Prometheus and Grafana in Docker, while the Spring Boot application runs on the Windows host.

## Start

Start MySQL, Redis, Prometheus, and Grafana:

```powershell
docker compose up -d
```

Start smartlife on the host:

```powershell
mvn spring-boot:run
```

## URLs

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000
- AlertManager: http://localhost:9093
- Spring Boot metrics: http://localhost:8081/actuator/prometheus
- Spring Boot health: http://localhost:8081/actuator/health

Grafana default login is usually `admin` / `admin`; Grafana may ask you to change the password on first login.

## Verify Prometheus Scraping

1. Open http://localhost:9090/targets.
2. Find the `smartlife` job.
3. Confirm the target is `UP`.

Prometheus runs inside Docker, so it scrapes the host Spring Boot process through:

```text
host.docker.internal:8081
```

Do not use `localhost:8081` in `prometheus.yml`, because inside the Prometheus container `localhost` means the Prometheus container itself.

## Recommended Queries

Use the Prometheus expression browser or Grafana Explore:

```promql
process_cpu_usage
jvm_memory_used_bytes
http_server_requests_seconds_count
system_cpu_usage
```

Useful examples:

```promql
rate(http_server_requests_seconds_count[1m])
jvm_memory_used_bytes{area="heap"}
```

## Alerting

The local alerting path is:

```text
smartlife /actuator/prometheus
  -> Prometheus
  -> monitoring/rules/smartlife-alert-rules.yml
  -> AlertManager
  -> http://host.docker.internal:8000/api/alerts
```

Alert rules are stored in:

```text
monitoring/rules/smartlife-alert-rules.yml
```

AlertManager webhook configuration is stored in:

```text
monitoring/alertmanager.yml
```

Prometheus also uses Blackbox Exporter to check the JSON body of:

```text
http://host.docker.internal:8081/api/health/detail
```

This allows Redis and MySQL dependency alerts to be based on the business health endpoint without changing application code.

## Verify Alert Rules

Open Prometheus rules:

```text
http://localhost:9090/rules
```

Open Prometheus alerts:

```text
http://localhost:9090/alerts
```

Open AlertManager:

```text
http://localhost:9093
```

## Failure Tests

### Service Down

Stop the Spring Boot application on the host. After the rule duration, Prometheus should fire:

```text
SmartLifeServiceDown
```

### Redis Unavailable

Stop the Redis container:

```powershell
docker stop smartlife-redis
```

The business health endpoint should return Redis as down, the Blackbox probe should fail the Redis body check, and Prometheus should fire:

```text
RedisUnavailable
```

Restart Redis after the test:

```powershell
docker start smartlife-redis
```

### MySQL Unavailable

Stop the MySQL container:

```powershell
docker stop smartlife-mysql
```

Prometheus should fire:

```text
MysqlUnavailable
```

Restart MySQL after the test:

```powershell
docker start smartlife-mysql
```

### CPU Alert

Start the built-in CPU fault injector:

```text
http://localhost:8081/test/fault/cpu
```

The alert rule is:

```text
SmartLifeHighCPUUsage
```

It fires after either `process_cpu_usage` stays above 80%, or
`fault_cpu_injection_active` stays at `1`, for 2 minutes. The explicit injection
signal makes this self-test deterministic on hosts and containers whose CPU
accounting does not allow the JVM metric to reach 80%.

Check or stop the injector with:

```text
http://localhost:8081/test/fault/cpu/status
http://localhost:8081/test/fault/cpu/stop
```

### JVM Memory Alert

Start gradual JVM heap pressure with:

```bash
curl http://localhost:8081/test/fault/oom
```

The injector retains 1 MB every 300 ms until heap usage is close to the
configured 93% target, then holds the objects without deliberately exhausting
the heap. `SmartLifeJvmMemoryHighUsage` fires after heap usage remains above 90% for
30 seconds and carries `service=smartlife`.

Stop the injector and release its retained objects with:

```bash
curl -X POST http://localhost:8081/test/fault/oom/stop
```

### HTTP Latency Alert

The rule `HighHttpLatency` uses:

```promql
http_server_requests_seconds_bucket
```

If this bucket metric is not present in `/actuator/prometheus`, enable HTTP server request histograms in the Spring Boot metrics configuration before validating P95 latency alerts.

## Python Agent Webhook

AlertManager posts alerts to:

```text
http://host.docker.internal:8000/api/alerts
```

A local Python FastAPI Agent should listen on the Windows host at port `8000`. AlertManager sends both firing and resolved events because `send_resolved` is enabled.
