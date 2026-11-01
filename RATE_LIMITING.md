# Brute Force Protection - Rate Limiting

## Обзор

Защита от brute force атак на login endpoint реализована через rate limiting по IP адресу.

### Два варианта защиты:

1. **Backend-native** (текущая реализация) - для single-instance или малых команд
2. **Nginx-based** (альтернатива) - для production с несколькими инстансами

---

## Backend Rate Limiting (текущая реализация)

### Компоненты

**Фильтр:** `LoginRateLimitFilter.java`
- Срабатывает только на `POST /api/auth/login`
- Отслеживает попытки по IP (с учётом X-Forwarded-For)
- Использует in-memory хранилище (ConcurrentHashMap)
- Периодическая очистка каждые 5 минут

**Исключение:** `RateLimitExceededException.java`
- Возвращает HTTP 429 Too Many Requests
- Включает Retry-After header

**Обработчик:** `GlobalExceptionHandler.handleRateLimitExceeded()`
- Возвращает JSON с информацией о блокировке

### Конфигурация

**application.properties:**
```properties
# Максимальное количество попыток
security.rate-limit.login.max-attempts=5

# Окно времени в секундах (300s = 5 минут)
security.rate-limit.login.window-seconds=300
```

**Environment variables:**
```bash
LOGIN_RATE_LIMIT_MAX_ATTEMPTS=5
LOGIN_RATE_LIMIT_WINDOW_SECONDS=300
```

### Поведение

**Нормальный flow:**
```
Attempt 1: 401 Unauthorized (wrong password)
Attempt 2: 401 Unauthorized
Attempt 3: 401 Unauthorized
Attempt 4: 401 Unauthorized
Attempt 5: 401 Unauthorized
Attempt 6: 429 Too Many Requests ← ЗАБЛОКИРОВАН
```

**JSON ответ при блокировке:**
```json
{
  "error": "Too Many Requests",
  "message": "Превышен лимит попыток входа. Попробуйте позже.",
  "retryAfter": 245
}
```

**Headers:**
```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 245
```

### Поддержка Reverse Proxy

Фильтр автоматически определяет реальный IP клиента:
1. Проверяет `X-Forwarded-For` header
2. Проверяет `X-Real-IP` header  
3. Использует `request.getRemoteAddr()` как fallback

**Пример X-Forwarded-For:**
```
X-Forwarded-For: 203.0.113.1, 198.51.100.1, 192.0.2.1
                 ^^^^^^^^^^^
                 Этот IP используется для rate limiting
```

### Ограничения

❌ **Не работает для distributed deployments** (несколько инстансов)
- Каждый инстанс имеет своё in-memory хранилище
- Атакующий может распределить запросы между инстансами

❌ **Сбрасывается при рестарте**
- Счётчики попыток хранятся в памяти
- После перезапуска все блокировки снимаются

✅ **Подходит для:**
- Single-instance deployments
- Малые команды (15-20 человек)
- Dev/staging окружения
- MVP без дополнительной инфраструктуры

### Тестирование

**Запуск тестов:**
```bash
cd dash_bord
./mvnw test -Dtest=LoginRateLimitTest
```

**Ручная проверка:**
```bash
# 5 попыток с неправильным паролем
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com","password":"wrong"}' \
    -w "\nHTTP: %{http_code}\n\n"
done

# 6-я попытка должна вернуть 429
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"wrong"}' \
  -v
```

---

## Nginx Rate Limiting (альтернатива)

### Когда использовать

✅ **Production с несколькими инстансами**
- Nginx перед несколькими backend серверами
- Централизованный rate limiting

✅ **Высокая нагрузка**
- Nginx обрабатывает rate limiting эффективнее
- Не нагружает backend приложение

### Конфигурация

**Файл:** `nginx-rate-limit.conf`

**Основные настройки:**
```nginx
# Определение зоны
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=5r/m;

# Применение к endpoint
location = /api/auth/login {
    limit_req zone=login_limit burst=3 nodelay;
    limit_req_status 429;
    add_header Retry-After 60 always;
    
    proxy_pass http://backend;
}
```

**Параметры:**
- `rate=5r/m` - 5 запросов в минуту
- `burst=3` - разрешает "всплеск" до 3 дополнительных запросов
- `zone=login_limit:10m` - 10MB памяти (~160k IP адресов)

### Развёртывание

**Docker Compose:**
```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx-rate-limit.conf:/etc/nginx/conf.d/default.conf
    depends_on:
      - backend
  
  backend:
    build: ./dash_bord
    environment:
      - SPRING_PROFILES_ACTIVE=prod
```

**Проверка конфигурации:**
```bash
nginx -t
nginx -s reload
```

**Мониторинг:**
```bash
# Логи rate limiting
grep "limiting requests" /var/log/nginx/error.log

# Статистика 429 ошибок
grep " 429 " /var/log/nginx/access.log | wc -l
```

### Настройка для X-Forwarded-For

Если Nginx за CloudFlare/Load Balancer:
```nginx
# Использовать первый IP из X-Forwarded-For
limit_req_zone $http_x_forwarded_for zone=login_limit:10m rate=5r/m;
```

### Production рекомендации

**Строгая защита:**
```nginx
limit_req zone=login_limit burst=1 nodelay;
# rate=3r/m → только 3 попытки в минуту
```

**Сбалансированная (по умолчанию):**
```nginx
limit_req zone=login_limit burst=3 nodelay;
# rate=5r/m → 5 попыток + burst 3
```

**Мягкая защита:**
```nginx
limit_req zone=login_limit burst=5 nodelay;
# rate=10r/m → 10 попыток + burst 5
```

---

## Сравнение подходов

| Критерий | Backend | Nginx |
|----------|---------|-------|
| **Multi-instance** | ❌ Не работает | ✅ Работает |
| **Простота** | ✅ Не требует Nginx | ⚠️ Нужен Nginx |
| **Производительность** | ⚠️ Нагружает backend | ✅ Обрабатывает в Nginx |
| **Гибкость** | ✅ Настройка через properties | ⚠️ Нужен доступ к Nginx |
| **Persistence** | ❌ Сбрасывается при рестарте | ✅ Сохраняется (если не рестарт Nginx) |
| **Testing** | ✅ Легко тестировать | ⚠️ Нужен интеграционный тест |
| **Observability** | ✅ Логи в backend | ✅ Логи в Nginx |

---

## Рекомендации

### Для текущего проекта (15-20 человек)

✅ **Использовать backend rate limiting**
- Достаточно для малой команды
- Не требует дополнительной инфраструктуры
- Легко настраивается и тестируется

### Для production масштабирования

✅ **Использовать Nginx rate limiting**
- Если планируется несколько инстансов backend
- Если Nginx уже есть в инфраструктуре
- Для максимальной производительности

### Для enterprise решения

✅ **Использовать Redis-based rate limiting**
- Distributed solution (работает на всех инстансах)
- Настраиваемая persistence
- Централизованное управление
- Требует Redis инфраструктуру

**Spring Boot + Redis пример:**
```java
@Component
public class RedisRateLimitFilter extends OncePerRequestFilter {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    // Использовать INCR + EXPIRE для счётчиков
}
```

---

## Дополнительные меры защиты

### 1. CAPTCHA после N попыток

```java
if (attempts >= 3) {
    // Требовать CAPTCHA для следующих попыток
    response.setHeader("X-Require-Captcha", "true");
}
```

### 2. Email уведомления

```java
if (rateLimitExceeded) {
    emailService.notifySecurityTeam(
        "Multiple failed login attempts from IP: " + clientIp
    );
}
```

### 3. Account lockout

```java
// Блокировка аккаунта после N неудачных попыток
if (failedAttempts >= 10) {
    user.setLocked(true);
    userRepository.save(user);
}
```

### 4. Мониторинг и алерты

```java
// Prometheus metrics
@Timed("auth.login.rate_limit")
public void handleRateLimit() {
    meterRegistry.counter("auth.login.blocked").increment();
}
```

---

## Troubleshooting

### Rate limit не работает

1. **Проверьте фильтр зарегистрирован:**
```bash
curl http://localhost:8080/actuator/beans | grep LoginRateLimitFilter
```

2. **Проверьте логи:**
```bash
grep "RATE_LIMIT" logs/spring.log
```

3. **Проверьте IP detection:**
```bash
# Должны видеть реальный IP в логах
[RATE_LIMIT] Login attempt from IP: 127.0.0.1 (attempts: 1/5)
```

### Блокируется легитимный пользователь

1. **Увеличьте лимит:**
```properties
security.rate-limit.login.max-attempts=10
```

2. **Уменьшите окно:**
```properties
security.rate-limit.login.window-seconds=60
```

3. **Добавьте whitelist IP:**
```java
if (isWhitelistedIp(clientIp)) {
    filterChain.doFilter(request, response);
    return;
}
```

### Rate limit сбрасывается слишком часто

1. **Проверьте cleanup scheduler:**
```java
// В LoginRateLimitFilter.java
scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 5, 5, TimeUnit.MINUTES);
```

2. **Увеличьте retention:**
```java
if (ageSeconds >= windowSeconds * 2) { // Удаляем записи старше двух окон
```

---

## Security Best Practices

✅ **Implemented:**
- Rate limiting по IP
- Поддержка X-Forwarded-For
- JSON error responses
- Retry-After headers
- Автоматическая очистка памяти

⚠️ **Recommended:**
- CAPTCHA после нескольких попыток
- Account lockout механизм
- Email уведомления администратора
- Мониторинг через Prometheus/Grafana

🔒 **Advanced:**
- Redis-based distributed rate limiting
- GeoIP блокировка подозрительных регионов
- ML-based anomaly detection
- Honeypot endpoints для детекции ботов
