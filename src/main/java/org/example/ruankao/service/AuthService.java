package org.example.ruankao.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.ruankao.common.BusinessException;
import org.example.ruankao.dto.AuthDtos;
import org.example.ruankao.entity.User;
import org.example.ruankao.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录认证：基于 Spring Security 会话认证，密码使用 BCrypt 存储。
 */
@Service
public class AuthService implements UserDetailsService, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 首次启动自动创建的默认账号，登录后可在「修改密码」中更换 */
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultUser();
    }

    /** 首次启动创建默认管理员账号 */
    @Transactional
    public void ensureDefaultUser() {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername(DEFAULT_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setDisplayName("管理员");
        admin.setRole("ADMIN");
        userRepository.save(admin);
        log.warn("已创建默认账号 {} / {}，请登录后及时修改密码", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                .roles(user.getRole())
                .build();
    }

    /** 登录：校验凭据并写入安全上下文（会话） */
    @Transactional
    public AuthDtos.UserResponse login(AuthDtos.LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String username = request.username() == null ? "" : request.username().trim();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("该账号已停用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        UserDetails principal = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(false)
                .roles(user.getRole())
                .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("用户登录成功: {}", username);
        return toResponse(user);
    }

    /** 当前登录状态；未登录时返回 authenticated=false */
    @Transactional(readOnly = true)
    public AuthDtos.StatusResponse status() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return new AuthDtos.StatusResponse(false, null);
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> new AuthDtos.StatusResponse(true, toResponse(user)))
                .orElseGet(() -> new AuthDtos.StatusResponse(false, null));
    }

    /** 修改当前登录用户的密码 */
    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        if (request.newPassword().equals(request.oldPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        User user = requireCurrentUser();
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("用户修改密码: {}", user.getUsername());
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("请先登录");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessException("登录状态已失效，请重新登录"));
    }

    private AuthDtos.UserResponse toResponse(User user) {
        return new AuthDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
    }
}
