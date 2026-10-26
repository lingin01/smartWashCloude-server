package com.company.project.domain.exception;

/** 页面接口可公开的失败原因，不把缺少业务实现包装成成功空列表。 */
public class PageApiException extends RuntimeException {
    private final int status;
    private final String errorCode;

    public PageApiException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() { return status; }
    public String errorCode() { return errorCode; }
}
