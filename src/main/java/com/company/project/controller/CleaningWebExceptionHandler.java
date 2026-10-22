package com.company.project.controller;
import com.company.project.domain.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 新接口输出可理解的提示；内部诊断由原有全局日志处理器记录。 */
@Order(0)
@RestControllerAdvice(assignableTypes = CleaningController.class)
public class CleaningWebExceptionHandler {
    @ExceptionHandler(AuthenticationException.class) public ResponseEntity<Result<Void>> authentication() {
        return ResponseEntity.status(401).body(Result.error(401, "请先登录"));
    }
    @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<Result<Void>> forbidden() {
        return ResponseEntity.status(403).body(Result.error(403, "当前账号没有相应权限"));
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Result<Void>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Result.error(e.getStatusCode().value(), e.getReason()));
    }
}
