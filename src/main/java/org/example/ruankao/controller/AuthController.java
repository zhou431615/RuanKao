package org.example.ruankao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.ruankao.common.ApiResponse;
import org.example.ruankao.dto.AuthDtos;
import org.example.ruankao.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录认证", description = "登录、登出、登录状态与密码修改")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "登录（成功后通过会话 Cookie 保持登录态）")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.UserResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        AuthDtos.UserResponse user = authService.login(request, httpRequest, httpResponse);
        return ApiResponse.ok("登录成功", user);
    }

    @Operation(summary = "查询当前登录状态（未登录返回 authenticated=false）")
    @GetMapping("/status")
    public ApiResponse<AuthDtos.StatusResponse> status() {
        return ApiResponse.ok(authService.status());
    }

    @Operation(summary = "修改当前登录用户的密码")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok("密码修改成功，下次登录请使用新密码", null);
    }
}
