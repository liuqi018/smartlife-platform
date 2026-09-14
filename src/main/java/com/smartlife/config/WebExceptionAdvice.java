package com.smartlife.config;

import com.smartlife.dto.Result;
import com.smartlife.agent.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().isEmpty() ? "invalid request" :
                e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().isEmpty() ? "invalid request" :
                e.getConstraintViolations().iterator().next().getMessage();
        return Result.fail(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleUnreadableRequest(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("invalid request body,method={},uri={},error={}",
                request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail("请求体不能为空或格式错误");
    }

    @ExceptionHandler(AgentException.class)
    public Result handleAgentException(AgentException e, HttpServletRequest request) {
        log.warn("agent request failed,method={},uri={},errorType={},error={}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("request failed,method={},uri={},errorType={},error={}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.fail("server error");
    }
}
