package com.company.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.project.domain.User;

public interface UserService {
    String login(String username, String password);
    User getById(Long id);
    User getByUsername(String username);
    Page<User> page(int page, int size, String keyword);
    void save(User user);
    void update(User user);
    void delete(Long id);
    void updatePassword(Long userId, String oldPassword, String newPassword);
}
