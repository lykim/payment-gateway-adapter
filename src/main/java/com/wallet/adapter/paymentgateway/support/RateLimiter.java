package com.wallet.adapter.paymentgateway.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter per provider.
 */
public class RateLimiter {
    
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final SecureLogger logger = new SecureLogger(RateLimiter.class.getName());
    
    public void configure(String providerId, int requestsPerSecond, int burstSize) {
        buckets.put(providerId, new TokenBucket(requestsPerSecond, burstSize));
        logger.info("Rate limiter configured for %s: %d req/s, burst %d", 
            providerId, requestsPerSecond, burstSize);
    }
    
    public boolean tryAcquire(String providerId) {
        TokenBucket bucket = buckets.get(providerId);
        if (bucket == null) return true;
        return bucket.tryAcquire();
    }
    
    public void acquire(String providerId) throws RateLimitExceededException {
        if (!tryAcquire(providerId)) {
            logger.warn("Rate limit exceeded for provider: %s", providerId);
            throw new RateLimitExceededException(
                "Rate limit exceeded for provider: " + providerId
            );
        }
    }
    
    public void reset(String providerId) {
        buckets.remove(providerId);
    }
    
    public void resetAll() {
        buckets.clear();
    }
    
    private static class TokenBucket {
        private final int refillRate;
        private final int capacity;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;
        
        TokenBucket(int refillRate, int capacity) {
            this.refillRate = refillRate;
            this.capacity = capacity;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }
        
        boolean tryAcquire() {
            refill();
            return tokens.getAndUpdate(t -> t > 0 ? t - 1 : t) > 0;
        }
        
        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            long tokensToAdd = (elapsed * refillRate) / 1000;
            
            if (tokensToAdd > 0) {
                tokens.updateAndGet(t -> Math.min(capacity, t + tokensToAdd));
                lastRefillTime = now;
            }
        }
    }
}
