package kr.co.kwt.gateway.filter.gateway;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ResponseLoggingGatewayFilterFactory extends AbstractGatewayFilterFactory<ResponseLoggingGatewayFilterFactory.Config> {
    private static final Logger logger = LoggerFactory.getLogger(ResponseLoggingGatewayFilterFactory.class);

    public ResponseLoggingGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                ServerHttpResponse response = exchange.getResponse();

                logger.info("Response Status Code: {}, Headers: {}", response.getStatusCode(), response.getHeaders());
            }));
        };
    }

    public static class Config {
    }
}