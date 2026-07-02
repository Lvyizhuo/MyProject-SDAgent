package com.shandong.policyagent.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态码与消息，由 GlobalExceptionHandler 统一处理。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
