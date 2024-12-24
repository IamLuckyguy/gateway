package kr.co.kwt.gateway.exception.handler;

import kr.co.kwt.gateway.exception.base.BaseException;
import kr.co.kwt.gateway.exception.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Order(-2) // Spring의 기본 WebExceptionHandler보다 높은 우선순위
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = createErrorResponse(exchange, ex);
        HttpStatusCode httpStatusCode = determineHttpStatus(ex);
        response.setStatusCode(httpStatusCode);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            logger.error("Error writing error response", e);
            return Mono.error(e);
        }
    }

    private ErrorResponse createErrorResponse(ServerWebExchange exchange, Throwable ex) {
        ErrorResponse.Builder builder = ErrorResponse.builder()
                .path(exchange.getRequest().getPath().value());

        if (ex instanceof BaseException baseException) {
            builder.code(baseException.getCode())
                    .message(baseException.getMessage());
        } else if (ex instanceof ResponseStatusException responseStatusException) {
            builder.code("GATEWAY_ERROR")
                    .message(responseStatusException.getMessage());
        } else {
            builder.code("INTERNAL_SERVER_ERROR")
                    .message(ex.getMessage());
        }

        return builder.build();
    }

    private HttpStatusCode determineHttpStatus(Throwable ex) {
        if (ex instanceof BaseException) {
            return ((BaseException) ex).getStatus();
        } else if (ex instanceof ResponseStatusException) {
            return ((ResponseStatusException) ex).getStatusCode();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}