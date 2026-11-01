# Production Observability Guide

## Endpoints

### Health Checks

**Liveness Probe** (для Docker/K8s):
```bash
curl http://localhost:8080/actuator/health/liveness
# Ответ: {"status":"UP"}
```

**Readiness Probe** (для Docker/K8s):
```bash
curl http://localhost:8080/actuator/health/readiness
# Ответ: {"status":"UP"}
```

**General Health** (показывает детали в dev, скрывает в prod):
```bash
curl http://localhost:8080/actuator/health
```

В production детали health checks доступны только для authenticated пользователей:
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8080/actuator/health
```

### Metrics

**Prometheus Metrics** (требует аутентификацию):
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8080/actuator/prometheus
```

**Application Metrics** (требует аутентификацию):
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8080/actuator/metrics
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8080/actuator/metrics/jvm.memory.used
```

### Application Info

**Публичная информация о приложении**:
```bash
curl http://localhost:8080/actuator/info
# Ответ: {"app":{"name":"demo","version":"0.0.1-SNAPSHOT"}}
```

## Docker Healthcheck

Dockerfile уже содержит HEALTHCHECK:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/actuator/health/liveness || exit 1
```

Проверка статуса контейнера:
```bash
docker ps  # Смотри колонку STATUS
# Healthy - контейнер работает
# Unhealthy - healthcheck провален
```

## Correlation ID Tracing

Все запросы автоматически получают correlation ID для трассировки.

**Отправка своего correlation ID**:
```bash
curl -H "X-Correlation-ID: my-trace-123" http://localhost:8080/api/tasks
```

**Проверка в логах**:
```
2024-04-08 10:15:23.456 [http-nio-8080-exec-1] [my-trace-123] INFO  c.e.d.controllers.TaskController - Processing request
```

Если не указать X-Correlation-ID, система сгенерирует UUID автоматически.

## Production Configuration

### Environment Variables

```bash
# Профиль
SPRING_PROFILES_ACTIVE=prod

# CORS
CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://app.yourdomain.com

# Database
DATABASE_URL=jdbc:postgresql://db:5432/yourdb
DB_USERNAME=secure_user
DB_PASSWORD=${DB_PASSWORD}  # из секретов
```

### Health Check Behavior

**Development** (`application-dev.properties`):
- Health details: **всегда показываются**
- Endpoints: health, info, metrics, prometheus

**Production** (`application-prod.properties`):
- Health details: **только для authenticated пользователей**
- Endpoints: health, metrics, prometheus (info excluded for security)

### Security

**Публичные endpoints**:
- `/actuator/health/**` - для Docker/K8s probes
- `/actuator/info` - метаданные приложения

**Защищённые endpoints** (требуют JWT):
- `/actuator/metrics/**`
- `/actuator/prometheus`

## Custom Health Indicators

Создан `ExternalServicesHealthIndicator` для проверки внешних зависимостей:
- Telegram API
- Google Gemini API

Добавьте свои проверки в метод `checkExternalServices()`.

## Prometheus Integration

Если используете Prometheus для мониторинга:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
    basic_auth:
      username: 'your_user'
      password: 'your_pass'
```

## Logs

**Формат лога с correlation ID**:
```
[timestamp] [thread] [correlationId] level logger - message
```

**Безопасность логов**:
- Автоматическая маскировка паролей, токенов, API keys
- Конфигурация в `logback-spring.xml`

## Kubernetes Probes

Пример манифеста:
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: app
    image: your-app:latest
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 40
      periodSeconds: 30
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 20
      periodSeconds: 10
```

## Troubleshooting

**Health check возвращает DOWN**:
1. Проверьте подключение к БД
2. Проверьте `ExternalServicesHealthIndicator`
3. Смотрите детали: `curl http://localhost:8080/actuator/health` (в dev)

**Metrics недоступны**:
- Убедитесь, что отправляете JWT token
- Проверьте, что профиль не скрыл endpoints

**Correlation ID не появляется в логах**:
- Убедитесь, что `CorrelationIdFilter` зарегистрирован
- Проверьте `logback-spring.xml` pattern

**Docker healthcheck fails**:
- Проверьте, что `wget` установлен в образе
- Увеличьте `start-period` если JVM долго стартует
