package sme.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sme.backend.entity.Warehouse;

import java.util.UUID;

/**
 * DTO an toàn để trả về thông tin kho hàng qua API.
 * Tránh serialize entity trực tiếp (gây vấn đề với Envers @Audited).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {

    private UUID id;
    private String code;
    private String name;
    private String provinceCode;
    private String address;
    private String phone;
    private UUID managerId;
    private Boolean isActive;
    
    private Warehouse.WarehouseType warehouseType;
    private Double latitude;
    private Double longitude;
    private Integer maxDailyOrders;
    private Boolean isAcceptingOrders;

    public static WarehouseResponse from(Warehouse w) {
        return WarehouseResponse.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .provinceCode(w.getProvinceCode())
                .address(w.getAddress())
                .phone(w.getPhone())
                .managerId(w.getManagerId())
                .isActive(w.getIsActive())
                .warehouseType(w.getWarehouseType())
                .latitude(w.getLatitude())
                .longitude(w.getLongitude())
                .maxDailyOrders(w.getMaxDailyOrders())
                .isAcceptingOrders(w.getIsAcceptingOrders())
                .build();
    }
}
