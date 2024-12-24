package kr.co.kwt.gateway.exception.base;

import org.springframework.http.HttpStatus;

public abstract class BaseException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    protected BaseException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}