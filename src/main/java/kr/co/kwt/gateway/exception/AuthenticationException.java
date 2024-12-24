package kr.co.kwt.gateway.exception;

import kr.co.kwt.gateway.exception.base.BaseException;
import org.springframework.http.HttpStatus;

public class AuthenticationException extends BaseException {
    private static final String DEFAULT_CODE = "AUTH_ERROR";
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.UNAUTHORIZED;

    public AuthenticationException(String message) {
        super(message, DEFAULT_CODE, DEFAULT_STATUS);
    }

    public AuthenticationException(String message, String code) {
        super(message, code, DEFAULT_STATUS);
    }

    public AuthenticationException(String message, String code, HttpStatus status) {
        super(message, code, status);
    }
}