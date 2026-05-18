package sme.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.AuditLogResponse;
import sme.backend.service.AuditLogService;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuditLogService auditLogService;

    /**
     * GET /api/admin/audit-logs
     * Lấy nhật ký thao tác toàn hệ thống (Chỉ Admin mới được xem)
     */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "100") int limit) {
        
        List<AuditLogResponse> logs = auditLogService.getGlobalAuditLogs(limit);
        return ResponseEntity.ok(ApiResponse.ok("Lấy nhật ký hệ thống thành công", logs));
    }
}