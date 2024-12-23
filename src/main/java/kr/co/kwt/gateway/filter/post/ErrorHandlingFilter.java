package kr.co.kwt.gateway.filter.post;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ErrorHandlingFilter extends AbstractGatewayFilterFactory<ErrorHandlingFilter.Config> {

    public ErrorHandlingFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return chain.filter(exchange)
                    .onErrorResume(error -> {
                        log.error("Error occurred during request processing: ", error);
                        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                        return Mono.empty();
                    });
        };
    }

    public static class Config {
    }
}