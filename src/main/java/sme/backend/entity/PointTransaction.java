package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_transactions")
@Audited
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PointTransaction extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "points_change", nullable = false)
    private Integer pointsChange;

    @Column(length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum TransactionStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }
}
