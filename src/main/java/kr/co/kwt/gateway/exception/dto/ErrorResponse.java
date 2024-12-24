package kr.co.kwt.gateway.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private final String timestamp;
    private final String code;
    private final String message;
    private final String path;
    private final Object details;

    private ErrorResponse(Builder builder) {
        this.timestamp = LocalDateTime.now().toString();
        this.code = builder.code;
        this.message = builder.message;
        this.path = builder.path;
        this.details = builder.details;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String code;
        private String message;
        private String path;
        private Object details;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder details(Object details) {
            this.details = details;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
    }
}