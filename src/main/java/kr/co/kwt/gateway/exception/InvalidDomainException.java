package kr.co.kwt.gateway.exception;

import kr.co.kwt.gateway.exception.base.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidDomainException extends BaseException {
    private static final String DEFAULT_CODE = "INVALID_DOMAIN";
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.FORBIDDEN;

    public InvalidDomainException(String message) {
        super(message, DEFAULT_CODE, DEFAULT_STATUS);
    }

    public InvalidDomainException(String message, String code) {
        super(message, code, DEFAULT_STATUS);
    }

    public InvalidDomainException(String message, String code, HttpStatus status) {
        super(message, code, status);
    }
}