package kr.co.kwt.gateway.filter.web;

import kr.co.kwt.gateway.exception.AuthenticationException;
import kr.co.kwt.gateway.exception.InvalidDomainException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorHandlingFilter implements WebFilter {
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandlingFilter.class);
    private final ObjectMapper objectMapper;

    public ErrorHandlingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(e -> handleError(exchange, e));
    }

    private Mono<Void> handleError(ServerWebExchange exchange, Throwable error) {
        logger.error("Error occurred during request processing", error);

        exchange.getResponse().setStatusCode(determineHttpStatus(error));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> errorResponse = Map.of(
                "status", "error",
                "message", error.getMessage(),
                "code", determineErrorCode(error)
        );

        byte[] errorResponseBytes;
        try {
            errorResponseBytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (Exception e) {
            logger.error("Error while serializing error response", e);
            errorResponseBytes = "Internal Server Error".getBytes();
        }

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(errorResponseBytes))
        );
    }

    private HttpStatus determineHttpStatus(Throwable error) {
        if (error instanceof InvalidDomainException) {
            return HttpStatus.FORBIDDEN;
        } else if (error instanceof AuthenticationException) {
            return HttpStatus.UNAUTHORIZED;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String determineErrorCode(Throwable error) {
        if (error instanceof InvalidDomainException) {
            return "INVALID_DOMAIN";
        } else if (error instanceof AuthenticationException) {
            return "AUTHENTICATION_FAILED";
        } else {
            return "INTERNAL_ERROR";
        }
    }
}