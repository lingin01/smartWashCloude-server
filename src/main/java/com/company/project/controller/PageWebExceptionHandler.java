package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.domain.exception.PageApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** 页面接口的公开错误协议；原有设备接口保持自己的兼容响应。 */
@Order(-1)
@RestControllerAdvice(assignableTypes = {CommandCenterController.class, AlarmEventController.class,
        DataExportController.class, AuthController.class, ReadOnlyUserController.class})
public class PageWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(PageWebExceptionHandler.class);
    public record ErrorData(String errorCode) {}

    @ExceptionHandler(PageApiException.class)
    public ResponseEntity<Result<ErrorData>> pageError(PageApiException error) {
        return failure(error.status(), error.errorCode(), error.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<ErrorData>> unauthenticated(AuthenticationException error) {
        return failure(401, "UNAUTHENTICATED", "请重新登录");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<ErrorData>> forbidden(AccessDeniedException error) {
        return failure(403, "FORBIDDEN", "账号不可用或没有相应权限");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<ErrorData>> validation(BindException error) {
        String errorCode = error.getBindingResult().getAllErrors().stream()
                .anyMatch(e -> e.getDefaultMessage() != null && e.getDefaultMessage().startsWith("RANGE_INCOMPLETE"))
                ? "RANGE_INCOMPLETE" : "INVALID_REQUEST";
        String message = error.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage()).filter(m -> m != null).sorted().findFirst().orElse("请求参数不正确");
        return failure(400, errorCode, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Result<ErrorData>> malformed(Exception error) {
        return failure(400, "INVALID_REQUEST", "请求参数格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<ErrorData>> invalid(IllegalArgumentException error) {
        return failure(400, "INVALID_REQUEST", error.getMessage());
    }
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Result<ErrorData>> versionConflict(Exception error) {
        return failure(409,"CONFIGURATION_VERSION_CONFLICT","配置已被修改，请读取最新版本后重试");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<ErrorData>> responseStatus(ResponseStatusException error) {
        return failure(error.getStatusCode().value(), "REQUEST_FAILED", error.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<ErrorData>> unexpected(Exception error) {
        if (error instanceof org.springframework.web.ErrorResponse response) {
            return failure(response.getStatusCode().value(), "INVALID_REQUEST", "请求方法或格式不受支持");
        }
        log.error("Page request failed", error);
        return failure(500, "INTERNAL_ERROR", "服务暂时无法完成请求");
    }

    private ResponseEntity<Result<ErrorData>> failure(int status, String code, String message) {
        return ResponseEntity.status(status).body(new Result<>(status, message, new ErrorData(code)));
    }
}
