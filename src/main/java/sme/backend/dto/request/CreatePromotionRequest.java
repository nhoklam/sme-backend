package sme.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class CreatePromotionRequest {

    @NotBlank(message = "Mã khuyến mãi không được trống")
    private String code;

    @NotBlank(message = "Tên khuyến mãi không được trống")
    private String name;

    private String description;

    @NotNull(message = "Loại giảm giá không được trống")
    private String discountType; // PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y

    @NotNull(message = "Giá trị giảm không được trống")
    private BigDecimal discountValue;

    private BigDecimal minOrderValue;

    private BigDecimal maxDiscountAmount;

    private Integer maxUsage;

    private UUID applicableProductId;

    private UUID applicableCategoryId;

    /** BUY_X_GET_Y */
    private Integer buyQuantity;
    private Integer getQuantity;

    @NotNull(message = "Ngày bắt đầu không được trống")
    private Instant startDate;

    @NotNull(message = "Ngày kết thúc không được trống")
    private Instant endDate;

    private String promotionSlot;
    private String applicableChannel;
    private String triggerType;
    private String conditionType;
    private String conditionValue;
}
