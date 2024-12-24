package kr.co.kwt.gateway.config;

import kr.co.kwt.gateway.config.properties.RateLimitProperties;
import kr.co.kwt.gateway.ratelimit.BucketRegistry;
import kr.co.kwt.gateway.ratelimit.memory.InMemoryBucketRegistry;
import kr.co.kwt.gateway.ratelimit.redis.RedisBucketRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RateLimitConfig {
    @Bean
    public BucketRegistry bucketRegistry(
            RedisTemplate<String, String> redisTemplate,
            RateLimitProperties rateLimitProperties
    ) {
        return new RedisBucketRegistry(redisTemplate, rateLimitProperties);
    }

//    InMemory 구현체 사용 예시 (로컬 환경에서 Redis 없이 테스트할 경우 참고)
//    @Bean
//    @Profile("local")
//    public BucketRegistry inMemoryBucketRegistry(RateLimitProperties rateLimitProperties) {
//        return new InMemoryBucketRegistry(rateLimitProperties);
//    }
}