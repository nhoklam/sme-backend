package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PromotionResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderValue;
    private BigDecimal maxDiscountAmount;
    private Integer maxUsage;
    private Integer usedCount;
    private UUID applicableProductId;
    private UUID applicableCategoryId;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Instant startDate;
    private Instant endDate;
    private Boolean isActive;
    private boolean valid; // computed field

    private String promotionSlot;
    private String applicableChannel;
    private String triggerType;
    private String conditionType;
    private String conditionValue;

    private Instant createdAt;
}
