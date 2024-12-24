package kr.co.kwt.gateway.ratelimit;

public interface BucketRegistry {
    boolean tryConsume(String key);
    void addTokens(String key, int tokens);
}