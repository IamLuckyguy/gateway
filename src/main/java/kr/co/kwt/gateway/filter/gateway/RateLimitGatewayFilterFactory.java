package kr.co.kwt.gateway.filter.gateway;

import kr.co.kwt.gateway.config.properties.RateLimitProperties;
import kr.co.kwt.gateway.ratelimit.BucketRegistry;
import kr.co.kwt.gateway.ratelimit.memory.InMemoryBucketRegistry;
import kr.co.kwt.gateway.ratelimit.redis.RedisBucketRegistry;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Rate Limit Filter 구현 가이드
 *
 * 1. 구조 개요
 * - RateLimitGatewayFilterFactory: Gateway Filter 구현체
 * - BucketRegistry: 토큰 버킷 관리 인터페이스
 * - RateLimitProperties: Rate Limit 설정 관리
 *
 * 2. 주요 컴포넌트
 *
 * 2.1 BucketRegistry (인터페이스)
 * - 토큰 버킷 알고리즘의 핵심 인터페이스
 * - Redis 또는 In-Memory 구현체 선택 가능
 *
 * 2.2 구현체
 * - InMemoryBucketRegistry: 단일 서버 환경용
 * - RedisBucketRegistry: 분산 환경용
 *
 * 2.3 RateLimitProperties
 * - 라우트별, 클라이언트 유형별 rate limit 설정 관리
 *
 * 3. 설정 방법
 *
 * 3.1 application.yml 기본 설정
 * ```yaml
 * gateway:
 *   rate-limit:
 *     defaultConfig:
 *       capacity: 100              # 최대 토큰 수
 *       refill-tokens: 10          # 보충 주기당 추가되는 토큰 수
 *       refill-period: 1s          # 토큰 보충 주기
 *       user:                      # 인증된 사용자 설정
 *         capacity: 200
 *         refill-tokens: 20
 *       api:                       # API 키 사용자 설정
 *         capacity: 150
 *         refill-tokens: 15
 *       ip:                        # IP 기반 설정
 *         capacity: 50
 *         refill-tokens: 5
 * ```
 *
 * 3.2 라우트별 설정
 * ```yaml
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: api-service
 *           uri: http://api-service
 *           predicates:
 *             - Path=/api/**
 *           filters:
 *             - name: RateLimit
 *               args:
 *                 routeConfig:
 *                   capacity: 200
 *                   refill-tokens: 20
 * ```
 *
 * 4. 동작 방식
 *
 * 4.1 클라이언트 식별
 * - JWT 토큰이 있는 경우: 사용자 ID 기반
 * - API 키가 있는 경우: API 키 기반
 * - 그 외: IP + User-Agent 조합
 *
 * 4.2 토큰 버킷 알고리즘
 * - 각 클라이언트별로 토큰 버킷 생성
 * - capacity만큼 토큰 보유 가능
 * - refill-period마다 refill-tokens만큼 토큰 보충
 * - 요청마다 1개의 토큰 소비
 *
 * 5. 모니터링
 * - 429 Too Many Requests: Rate Limit 초과 시 응답
 * - 로그 확인: RateLimitGatewayFilterFactory의 warn 로그
 *
 * 6. 운영 시 고려사항
 *
 * 6.1 Redis 사용 시
 * - Redis 연결 설정 필수
 * - Redis 장애 대비 fallback 전략 수립
 *
 * 6.2 In-Memory 사용 시
 * - 단일 서버에서만 유효
 * - 서버 재시작 시 카운터 초기화
 *
 * 7. 확장 포인트
 * - BucketRegistry 인터페이스 구현으로 새로운 저장소 추가 가능
 * - 클라이언트 식별 로직 커스터마이징 가능
 *
 * 8. 트러블슈팅
 *
 * 8.1 일반적인 문제
 * - Rate Limit 초과: 클라이언트에게 429 응답
 * - Redis 연결 실패: 로그 확인 및 연결 설정 검증
 *
 * 8.2 모니터링 포인트
 * - Rate Limit 초과 빈도
 * - Redis 연결 상태
 * - 버킷별 토큰 소비 패턴
 *
 * @see BucketRegistry
 * @see RateLimitProperties
 * @see InMemoryBucketRegistry
 * @see RedisBucketRegistry
 */
@Component
public class RateLimitGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimitGatewayFilterFactory.Config> {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitGatewayFilterFactory.class);

    private final BucketRegistry bucketRegistry;
    private final RateLimitProperties defaultProperties;

    public RateLimitGatewayFilterFactory(BucketRegistry bucketRegistry, RateLimitProperties rateLimitProperties) {
        super(Config.class);
        this.bucketRegistry = bucketRegistry;
        this.defaultProperties = rateLimitProperties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String clientId = getClientIdentifier(request);
            String routeId = extractRouteId(request);

            // 라우트별 설정 또는 기본 설정 사용
            RateLimitProperties.RouteConfig routeConfig =
                    config.getRouteConfig() != null ? config.getRouteConfig() :
                            defaultProperties.getRoutes().getOrDefault(routeId, defaultProperties.getDefaultConfig());

            String bucketKey = generateBucketKey(routeId, clientId);

            // Rate limit check
            if (!bucketRegistry.tryConsume(bucketKey)) {
                return handleRateLimitExceeded(exchange, clientId, routeId);
            }

            return chain.filter(exchange)
                    .doOnError(throwable -> logger.error("Error processing request", throwable));
        };
    }

    private String getClientIdentifier(ServerHttpRequest request) {
        // JWT 토큰에서 사용자 ID 추출 시도
        String userId = extractUserIdFromJwt(request);
        if (StringUtils.hasText(userId)) {
            return "user:" + userId;
        }

        // API 키 확인
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        if (StringUtils.hasText(apiKey)) {
            return "api:" + apiKey;
        }

        // IP + User-Agent 조합 사용
        String ip = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        return String.format("ip:%s:agent:%s", ip,
                StringUtils.hasText(userAgent) ? userAgent.hashCode() : "unknown");
    }

    private String extractUserIdFromJwt(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // JWT 토큰 파싱 및 사용자 ID 추출 로직
            // 실제 구현은 JWT 라이브러리를 사용하여 구현
            return null; // 임시 반환
        }
        return null;
    }

    private String extractRouteId(ServerHttpRequest request) {
        // 요청 경로에서 라우트 ID 추출
        String path = request.getPath().value();
        return path.split("/")[1]; // 첫 번째 경로 세그먼트를 라우트 ID로 사용
    }

    private String generateBucketKey(String routeId, String clientId) {
        return String.format("%s:%s", routeId, clientId);
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, String clientId, String routeId) {
        logger.warn("Rate limit exceeded - Client: {}, Route: {}", clientId, routeId);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    @Setter
    @Getter
    public static class Config {
        private RateLimitProperties.RouteConfig routeConfig;

    }
}