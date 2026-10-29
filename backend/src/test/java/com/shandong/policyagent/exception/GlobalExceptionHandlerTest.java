package com.shandong.policyagent.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全局异常处理器的状态码映射。
 *
 * 重点回归：未命中路由必须返回 404，而不是被兜底分支包装成 500。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn404WhenResourceNotFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "v3/api-docs");

        ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("请求的资源不存在", response.getBody().get("message"));
    }

    @Test
    void shouldReturn405WhenMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<Map<String, Object>> response =
                handler.handleHttpRequestMethodNotSupportedException(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(405, response.getBody().get("status"));
    }
}
