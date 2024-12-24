package kr.co.kwt.gateway.ratelimit.memory;

import kr.co.kwt.gateway.config.properties.RateLimitProperties;
import kr.co.kwt.gateway.ratelimit.BucketRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBucketRegistry implements BucketRegistry {
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public InMemoryBucketRegistry(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized boolean tryConsume(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(properties.getDefaultConfig().getCapacity(),
                        properties.getDefaultConfig().getRefillTokens(),
                        properties.getDefaultConfig().getRefillPeriod()));
        return bucket.tryConsume();
    }

    @Override
    public synchronized void addTokens(String key, int tokens) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(properties.getDefaultConfig().getCapacity(),
                        properties.getDefaultConfig().getRefillTokens(),
                        properties.getDefaultConfig().getRefillPeriod()));
        bucket.addTokens(tokens);
    }

    private static class TokenBucket {
        private int tokens;
        private Instant lastRefillTime;
        private final int capacity;
        private final int refillTokens;
        private final Duration refillPeriod;

        public TokenBucket(int capacity, int refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = refillPeriod;
            this.tokens = capacity;
            this.lastRefillTime = Instant.now();
        }

        public synchronized boolean tryConsume() {
            refill();

            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        public synchronized void addTokens(int count) {
            refill();
            tokens = Math.min(capacity, tokens + count);
        }

        private void refill() {
            Instant now = Instant.now();
            Duration timePassed = Duration.between(lastRefillTime, now);

            if (timePassed.compareTo(refillPeriod) < 0) {
                return;
            }

            long periodsElapsed = timePassed.toMillis() / refillPeriod.toMillis();
            int tokensToAdd = (int) (periodsElapsed * refillTokens);

            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                Duration remainder = timePassed.minus(
                        refillPeriod.multipliedBy(periodsElapsed)
                );
                lastRefillTime = now.minus(remainder);
            }
        }
    }

    // For testing and monitoring purposes
    public int getAvailableTokens(String key) {
        return buckets.containsKey(key) ? buckets.get(key).tokens : properties.getDefaultConfig().getCapacity();
    }
}