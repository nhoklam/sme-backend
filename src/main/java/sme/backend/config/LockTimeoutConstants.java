package sme.backend.config;

/**
 * NV-2: Hằng số timeout cho Pessimistic Locking trên Inventory.
 * Sử dụng String vì JPA @QueryHint yêu cầu compile-time String constant.
 *
 * POS_LOCK_TIMEOUT_MS  = "3000"  (3 giây) — Fail-fast cho luồng thu ngân real-time.
 * ADMIN_LOCK_TIMEOUT_MS = "10000" (10 giây) — Cho phép Admin đợi lâu hơn khi POS đang giữ khóa.
 */
public final class LockTimeoutConstants {

    private LockTimeoutConstants() {
        // Utility class — không cho phép khởi tạo
    }

    /** Timeout cho POS Checkout / Refund / Void — 3 giây */
    public static final String POS_LOCK_TIMEOUT_MS = "3000";

    /** Timeout cho Admin Import Stock / Adjust Inventory — 10 giây */
    public static final String ADMIN_LOCK_TIMEOUT_MS = "10000";
}
