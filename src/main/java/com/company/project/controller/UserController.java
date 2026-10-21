package com.company.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.project.domain.PageResult;
import com.company.project.domain.Result;
import com.company.project.domain.User;
import com.company.project.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/page")
    public Result<PageResult<User>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Page<User> result = userService.page(page, size, keyword);
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(PageResult.of(result.getTotal(), result.getCurrent(),
                result.getSize(), result.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    @PostMapping
    public Result<?> save(@RequestBody User user) {
        userService.save(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.update(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/password")
    public Result<?> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.updatePassword(id, body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }
}
