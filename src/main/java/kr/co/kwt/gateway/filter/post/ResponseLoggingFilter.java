package kr.co.kwt.gateway.filter.post;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ResponseLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    Long requestTime = exchange.getAttribute("requestTime");
                    if (requestTime != null) {
                        long duration = System.currentTimeMillis() - requestTime;
                        ServerHttpResponse response = exchange.getResponse();
                        HttpStatusCode httpStatusCode = response.getStatusCode();

                        log.info("Response: Status: {}, Duration: {}ms",
                                httpStatusCode, duration);
                    }
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}