package org.example.ruankao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录认证相关 DTO。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 登录请求 */
    public record LoginRequest(
            @NotBlank(message = "请输入用户名") String username,
            @NotBlank(message = "请输入密码") String password) {
    }

    /** 当前登录状态 */
    public record StatusResponse(boolean authenticated, UserResponse user) {
    }

    /** 用户信息（不含任何凭据） */
    public record UserResponse(Long id, String username, String displayName,
                               String role, String lastLoginAt) {
    }

    /** 修改密码请求 */
    public record ChangePasswordRequest(
            @NotBlank(message = "请输入当前密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位") String newPassword,
            @NotBlank(message = "请再次输入新密码") String confirmPassword) {
    }
}
