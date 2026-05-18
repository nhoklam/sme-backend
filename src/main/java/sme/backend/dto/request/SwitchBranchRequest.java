package sme.backend.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class SwitchBranchRequest {
    // Nullable — null = quay về chế độ xem toàn hệ thống
    private UUID warehouseId;
}
