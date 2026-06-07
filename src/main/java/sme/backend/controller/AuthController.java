package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.ChangePasswordRequest;
import sme.backend.dto.request.CreateUserRequest;
import sme.backend.dto.request.LoginRequest;
import sme.backend.dto.request.ResetPasswordRequest;
import sme.backend.dto.request.SwitchBranchRequest;
import sme.backend.dto.request.CustomerRegisterRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.AuthResponse;
import sme.backend.dto.response.UserResponse;
import sme.backend.security.UserPrincipal;
import sme.backend.service.AuthService;
import sme.backend.entity.User;
import sme.backend.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /auth/admin/login - Dành cho nhân viên */
    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập Admin thành công", authService.adminLogin(req)));
    }

    /** POST /auth/login - Dành cho khách hàng (Online) */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> customerLogin(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập Khách hàng thành công", authService.customerLogin(req)));
    }

    /** POST /auth/customer/register */
    @PostMapping("/customer/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerCustomer(
            @Valid @RequestBody CustomerRegisterRequest req) {
        AuthResponse response = authService.registerCustomer(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /** POST /auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<AuthResponse>builder()
                            .success(false).message("refreshToken bắt buộc").build());
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(refreshToken)));
    }

    /** POST /auth/oauth2/token */
    @PostMapping("/oauth2/token")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeOAuth2Token(
            @RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "Mã xác thực là bắt buộc");
        }
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authService.exchangeOAuth2Token(code)));
    }

    /** POST /auth/switch-branch */
    @PostMapping("/switch-branch")
    public ResponseEntity<ApiResponse<AuthResponse>> switchBranch(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SwitchBranchRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Chuyển chi nhánh thành công",
                authService.switchBranch(principal, req.getWarehouseId())));
    }

    /** GET /auth/me */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.getUserResponseById(principal.getId())));
    }

    /** PUT /auth/change-password */
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(principal.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", null));
    }

    /** POST /auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String accessToken = null;
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            accessToken = bearerToken.substring(7);
        }
        String refreshToken = body != null ? body.get("refreshToken") : null;
        
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }

    /** POST /auth/forgot-password */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "Email là bắt buộc");
        }
        // Get client IP for rate limiting
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        authService.forgotPassword(email, ip);
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP đã được gửi đến email", null));
    }

    /** POST /auth/reset-password */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        String email = req.getEmail();
        String otp = req.getOtp();
        String newPassword = req.getNewPassword();
        
        authService.resetPassword(email, otp, newPassword);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", null));
    }

    /** POST /auth/unlock-user */
    @PostMapping("/unlock-user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUser(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "Username là bắt buộc");
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        String userAgent = request.getHeader("User-Agent");
        
        authService.unlockUser(username, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Mở khóa tài khoản thành công", null));
    }

    // ── User Management (ADMIN/MANAGER) ──────────────────────────

    /** POST /auth/users */
    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserRequest req) {
        if (principal.getRole() == User.UserRole.ROLE_MANAGER) {
            if ("ROLE_ADMIN".equals(req.getRole()) || "ROLE_MANAGER".equals(req.getRole())) {
                throw new BusinessException("FORBIDDEN", "Quản lý không có quyền tạo tài khoản Admin hoặc Manager");
            }
            req.setWarehouseId(principal.getWarehouseId());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(authService.createUser(req)));
    }

    /** PATCH /auth/users/{id}/activate */
    @PatchMapping("/users/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        if (principal.getRole() == User.UserRole.ROLE_MANAGER) {
            authService.validateManagerAccessToUser(principal.getWarehouseId(), id);
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.toggleUserActive(id, true)));
    }

    /** PATCH /auth/users/{id}/deactivate */
    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        if (principal.getRole() == User.UserRole.ROLE_MANAGER) {
            authService.validateManagerAccessToUser(principal.getWarehouseId(), id);
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.toggleUserActive(id, false)));
    }

    /** GET /auth/users */
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID warehouseId) {
        UUID searchWarehouseId = warehouseId;
        if (principal.getRole() == User.UserRole.ROLE_MANAGER) {
            searchWarehouseId = principal.getWarehouseId();
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.searchUsers(keyword, role, searchWarehouseId)));
    }

    /** PUT /auth/users/{id} */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody CreateUserRequest req) {
        if (principal.getRole() == User.UserRole.ROLE_MANAGER) {
            authService.validateManagerAccessToUser(principal.getWarehouseId(), id);
            if ("ROLE_ADMIN".equals(req.getRole()) || "ROLE_MANAGER".equals(req.getRole())) {
                throw new BusinessException("FORBIDDEN", "Quản lý không có quyền gán quyền Admin hoặc Manager");
            }
            req.setWarehouseId(principal.getWarehouseId());
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.updateUser(id, req)));
    }
}