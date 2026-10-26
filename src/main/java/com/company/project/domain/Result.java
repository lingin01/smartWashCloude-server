package com.company.project.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }

    public static Result<Void> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static Result<Void> error(String message) {
        return new Result<>(500, message, null);
    }
}
