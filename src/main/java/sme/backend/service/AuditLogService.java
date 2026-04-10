package sme.backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.response.AuditLogResponse;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getGlobalAuditLogs(int limit) {
        // Query chuẩn vào các bảng _audit do Envers quản lý
        // revtype: 0 = Thêm mới, 1 = Cập nhật, 2 = Xóa
        String sql = """
            SELECT * FROM (
                SELECT 'Người dùng' as entity_name, id as entity_id, revtype,
                       COALESCE(updated_by, created_by, 'SYSTEM') as changed_by, 
                       rev, COALESCE(updated_at, created_at) as changed_at 
                FROM users_audit
                
                UNION ALL
                
                SELECT 'Sản phẩm' as entity_name, id as entity_id, revtype,
                       COALESCE(updated_by, created_by, 'SYSTEM') as changed_by, 
                       rev, COALESCE(updated_at, created_at) as changed_at 
                FROM products_audit
                
                UNION ALL
                
                SELECT 'Chi nhánh' as entity_name, id as entity_id, revtype,
                       COALESCE(updated_by, created_by, 'SYSTEM') as changed_by, 
                       rev, COALESCE(updated_at, created_at) as changed_at 
                FROM warehouses_audit
                
                UNION ALL
                
                SELECT 'Đơn hàng' as entity_name, id as entity_id, revtype,
                       COALESCE(updated_by, created_by, 'SYSTEM') as changed_by, 
                       rev, COALESCE(updated_at, created_at) as changed_at 
                FROM orders_audit
            ) AS combined_audit
            WHERE changed_at IS NOT NULL
            ORDER BY changed_at DESC
            LIMIT :limit
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        List<AuditLogResponse> logs = new ArrayList<>();

        for (Object[] row : results) {
            String entityName = (String) row[0];
            UUID entityId = (UUID) row[1];
            
            // Xử lý loại hành động từ revtype của Envers
            Number revtypeNum = (Number) row[2];
            String actionType = switch (revtypeNum.intValue()) {
                case 0 -> "CREATE";
                case 1 -> "UPDATE";
                case 2 -> "DELETE";
                default -> "UNKNOWN";
            };

            String changedBy = (String) row[3];
            Integer revision = ((Number) row[4]).intValue();
            
            Instant changedAt = null;
            if (row[5] != null) {
                if (row[5] instanceof Timestamp ts) {
                    changedAt = ts.toInstant();
                } else if (row[5] instanceof java.util.Date d) {
                    changedAt = d.toInstant();
                }
            }

            logs.add(AuditLogResponse.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .actionType(actionType)
                    .changedBy(changedBy)
                    .revision(revision)
                    .changedAt(changedAt)
                    .build());
        }

        return logs;
    }
}