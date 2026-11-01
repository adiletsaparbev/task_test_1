package com.example.demo.exception;

/**
 * Исключение при превышении rate limit для защиты от brute force атак.
 * Возвращает HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
