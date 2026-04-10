package sme.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private String entityName;   // Tên bảng/đối tượng (VD: Sản phẩm, Đơn hàng)
    private UUID entityId;       // ID của đối tượng bị thay đổi
    private String actionType;   // Hành động (CREATE, UPDATE, DELETE)
    private String changedBy;    // Người thực hiện (Lấy từ updated_by / created_by)
    private Instant changedAt;   // Thời gian thực hiện
    private Integer revision;    // Số thứ tự phiên bản (Rev)
}