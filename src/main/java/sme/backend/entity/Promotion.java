package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.NotAudited;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Chương trình khuyến mãi.
 * Hỗ trợ 3 loại:
 * - PERCENTAGE: Giảm theo % trên tổng đơn
 * - FIXED_AMOUNT: Giảm số tiền cố định
 * - BUY_X_GET_Y: Mua X tặng Y (dùng buyQuantity, getQuantity)
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion extends BaseSimpleEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    /** Giá trị giảm: % hoặc số tiền cố định */
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    /** Giá trị đơn hàng tối thiểu để áp dụng */
    @Column(name = "min_order_value", precision = 15, scale = 2)
    private BigDecimal minOrderValue;

    /** Số tiền giảm tối đa (cho loại PERCENTAGE) */
    @Column(name = "max_discount_amount", precision = 15, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Số lần sử dụng tối đa (null = không giới hạn) */
    @Column(name = "max_usage")
    private Integer maxUsage;

    /** Số lần đã sử dụng */
    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    /** Áp dụng cho sản phẩm cụ thể (null = tất cả) */
    @Column(name = "applicable_product_id")
    private UUID applicableProductId;

    /** Áp dụng cho danh mục cụ thể (null = tất cả) */
    @Column(name = "applicable_category_id")
    private UUID applicableCategoryId;

    /** BUY_X_GET_Y: Số lượng cần mua */
    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    /** BUY_X_GET_Y: Số lượng tặng */
    @Column(name = "get_quantity")
    private Integer getQuantity;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // --- PHASE 2 FIELDS ---
    // --- PHASE 2 FIELDS ---
    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_slot", length = 30)
    @Builder.Default
    private PromotionSlot promotionSlot = PromotionSlot.ORDER;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_channel", length = 30)
    @Builder.Default
    private ApplicableChannel applicableChannel = ApplicableChannel.ALL;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 30)
    @Builder.Default
    private TriggerType triggerType = TriggerType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", length = 50)
    private ConditionType conditionType;

    @Column(name = "condition_value", length = 255)
    private String conditionValue;

    public enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y
    }

    public enum PromotionSlot {
        ORDER, SHIPPING
    }

    public enum ApplicableChannel {
        ONLINE, POS, ALL
    }

    public enum TriggerType {
        MANUAL, AUTO
    }

    public enum ConditionType {
        MIN_ORDER_VALUE, SPECIFIC_PRODUCT, CUSTOMER_GROUP, DAY_OF_WEEK
    }

    /** Kiểm tra KM còn hiệu lực và đúng kênh */
    public boolean isValid(String currentChannel) {
        Instant now = Instant.now();
        boolean validTimeAndUsage = Boolean.TRUE.equals(isActive)
                && (startDate == null || !now.isBefore(startDate))
                && (endDate == null || !now.isAfter(endDate))
                && (maxUsage == null || usedCount < maxUsage);

        if (!validTimeAndUsage)
            return false;

        if (currentChannel != null && this.applicableChannel != ApplicableChannel.ALL) {
            if (!this.applicableChannel.name().equalsIgnoreCase(currentChannel)) {
                return false;
            }
        }
        return true;
    }

    /** Kiểm tra KM còn hiệu lực (không có channel) */
    public boolean isValid() {
        return isValid(null);
    }

    /** Tính số tiền giảm cho đơn hàng */
    public BigDecimal calculateDiscount(BigDecimal orderTotal) {
        if (!isValid())
            return BigDecimal.ZERO;

        // Sử dụng doubleValue() để so sánh cho chắc chắn, tránh lỗi scale/precision của
        // BigDecimal
        double total = orderTotal != null ? orderTotal.doubleValue() : 0;
        double min = minOrderValue != null ? minOrderValue.doubleValue() : 0;

        if (total < min)
            return BigDecimal.ZERO;

        BigDecimal discount = BigDecimal.ZERO;
        switch (discountType) {
            case PERCENTAGE:
                discount = orderTotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 0,
                        java.math.RoundingMode.HALF_UP);
                if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                    discount = maxDiscountAmount;
                }
                break;
            case FIXED_AMOUNT:
                discount = discountValue.min(orderTotal);
                break;
            default:
                return BigDecimal.ZERO;
        }
        return discount;
    }

    public void incrementUsage() {
        this.usedCount = (this.usedCount != null ? this.usedCount : 0) + 1;
    }
}
