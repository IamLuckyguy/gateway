package kr.co.kwt.gateway.ratelimit.redis;

import kr.co.kwt.gateway.config.properties.RateLimitProperties;
import kr.co.kwt.gateway.ratelimit.BucketRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class RedisBucketRegistry implements BucketRegistry {
    private static final Logger log = LoggerFactory.getLogger(RedisBucketRegistry.class);
    private static final String BUCKET_KEY_PREFIX = "rate_limit:";
    private static final String LAST_REFILL_SUFFIX = ":last_refill";

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties properties;

    public RedisBucketRegistry(RedisTemplate<String, String> redisTemplate,
                               RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean tryConsume(String key) {
        String bucketKey = BUCKET_KEY_PREFIX + key;
        String lastRefillKey = bucketKey + LAST_REFILL_SUFFIX;

        try {
            Boolean result = redisTemplate.execute(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public Boolean execute(RedisOperations operations) throws DataAccessException {
                    try {
                        // Watch both keys for changes
                        operations.watch(bucketKey);
                        operations.watch(lastRefillKey);

                        // Get current values
                        String tokenStr = (String) operations.opsForValue().get(bucketKey);
                        String lastRefillStr = (String) operations.opsForValue().get(lastRefillKey);

                        // Parse current tokens and last refill time
                        int currentTokens = parseTokens(tokenStr);
                        Instant lastRefillTime = parseLastRefill(lastRefillStr);

                        // Calculate refill
                        Instant now = Instant.now();
                        int tokensToAdd = calculateTokensToAdd(lastRefillTime, now);
                        currentTokens = Math.min(properties.getDefaultConfig().getCapacity(), currentTokens + tokensToAdd);

                        // If we have tokens available
                        if (currentTokens > 0) {
                            operations.multi();

                            // Update tokens
                            operations.opsForValue().set(bucketKey, String.valueOf(currentTokens - 1));

                            // Update last refill time
                            operations.opsForValue().set(lastRefillKey, String.valueOf(now.toEpochMilli()));

                            // Set expiry for both keys
                            operations.expire(bucketKey, Duration.ofHours(1));
                            operations.expire(lastRefillKey, Duration.ofHours(1));

                            List<Object> results = operations.exec();
                            return !results.isEmpty();
                        }

                        operations.unwatch();
                        return false;

                    } catch (Exception e) {
                        operations.unwatch();
                        log.error("Error during Redis operation", e);
                        return false;
                    }
                }
            });

            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.error("Failed to execute rate limit check", e);
            return false;
        }
    }

    @Override
    public void addTokens(String key, int tokens) {
        if (tokens <= 0) return;

        String bucketKey = BUCKET_KEY_PREFIX + key;
        try {
            redisTemplate.execute(new SessionCallback<Void>() {
                @Override
                @SuppressWarnings("unchecked")
                public Void execute(RedisOperations operations) throws DataAccessException {
                    operations.watch(bucketKey);

                    String tokenStr = (String) operations.opsForValue().get(bucketKey);
                    int currentTokens = parseTokens(tokenStr);

                    operations.multi();
                    operations.opsForValue().set(bucketKey,
                            String.valueOf(Math.min(properties.getDefaultConfig().getCapacity(), currentTokens + tokens)));
                    operations.expire(bucketKey, Duration.ofHours(1));
                    operations.exec();

                    return null;
                }
            });
        } catch (Exception e) {
            log.error("Failed to add tokens", e);
        }
    }

    private int parseTokens(String tokenStr) {
        if (tokenStr == null) {
            return properties.getDefaultConfig().getCapacity();
        }
        try {
            return Math.min(properties.getDefaultConfig().getCapacity(),
                    Math.max(0, Integer.parseInt(tokenStr.trim())));
        } catch (NumberFormatException e) {
            log.warn("Invalid token count in Redis: {}", tokenStr);
            return properties.getDefaultConfig().getCapacity();
        }
    }

    private Instant parseLastRefill(String lastRefillStr) {
        if (lastRefillStr == null) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(lastRefillStr.trim()));
        } catch (NumberFormatException e) {
            log.warn("Invalid last refill time in Redis: {}", lastRefillStr);
            return Instant.now();
        }
    }

    private int calculateTokensToAdd(Instant lastRefillTime, Instant now) {
        Duration timePassed = Duration.between(lastRefillTime, now);
        RateLimitProperties.RouteConfig config = properties.getDefaultConfig();

        if (timePassed.compareTo(config.getRefillPeriod()) < 0) {
            return 0;
        }

        return calculateTokensForPeriod(timePassed, config);
    }

    private int calculateTokensForPeriod(Duration timePassed, RateLimitProperties.RouteConfig config) {
        long periodsElapsed = timePassed.toMillis() / config.getRefillPeriod().toMillis();
        return (int) (periodsElapsed * config.getRefillTokens());
    }
}