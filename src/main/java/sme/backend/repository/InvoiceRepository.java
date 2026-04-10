package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByCode(String code);

    boolean existsByCode(String code);

    Page<Invoice> findByShiftIdOrderByCreatedAtDesc(UUID shiftId, Pageable pageable);

    Page<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    // Lấy hóa đơn với items (tránh N+1 query)
    @Query("""
        SELECT DISTINCT i FROM Invoice i
        LEFT JOIN FETCH i.items
        WHERE i.id = :id
        """)
    Optional<Invoice> findByIdWithDetails(@Param("id") UUID id);

    // Doanh thu theo ca
    @Query("""
        SELECT COALESCE(SUM(i.finalAmount), 0) FROM Invoice i
        WHERE i.shiftId = :shiftId AND i.type = 'SALE'
        """)
    BigDecimal sumRevenueByShift(@Param("shiftId") UUID shiftId);

    // Báo cáo doanh thu theo NGÀY
    @Query(value = """
        SELECT
            DATE_TRUNC('day', i.created_at) AS period,
            COUNT(DISTINCT i.id) AS invoice_count,
            SUM(i.final_amount) AS revenue,
            SUM(COALESCE(item_agg.total_cogs, 0)) AS cogs,
            SUM(i.final_amount) - SUM(COALESCE(item_agg.total_cogs, 0)) AS gross_profit
        FROM invoices i
        JOIN shifts s ON s.id = i.shift_id
        LEFT JOIN (
            SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
            FROM invoice_items
            GROUP BY invoice_id
        ) item_agg ON item_agg.invoice_id = i.id
        WHERE (CAST(:wid AS uuid) IS NULL OR s.warehouse_id = CAST(:wid AS uuid))
        AND i.type = 'SALE'
        AND i.created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC('day', i.created_at)
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportDaily(@Param("wid")    UUID warehouseId,
                                                    @Param("from")   Instant from,
                                                    @Param("to")     Instant to);

    // Báo cáo doanh thu theo TUẦN
    @Query(value = """
        SELECT
            DATE_TRUNC('week', i.created_at) AS period,
            COUNT(DISTINCT i.id) AS invoice_count,
            SUM(i.final_amount) AS revenue,
            SUM(COALESCE(item_agg.total_cogs, 0)) AS cogs,
            SUM(i.final_amount) - SUM(COALESCE(item_agg.total_cogs, 0)) AS gross_profit
        FROM invoices i
        JOIN shifts s ON s.id = i.shift_id
        LEFT JOIN (
            SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
            FROM invoice_items
            GROUP BY invoice_id
        ) item_agg ON item_agg.invoice_id = i.id
        WHERE (CAST(:wid AS uuid) IS NULL OR s.warehouse_id = CAST(:wid AS uuid))
        AND i.type = 'SALE'
        AND i.created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC('week', i.created_at)
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportWeekly(@Param("wid")    UUID warehouseId,
                                                     @Param("from")   Instant from,
                                                     @Param("to")     Instant to);

    // Báo cáo doanh thu theo THÁNG
    @Query(value = """
        SELECT
            DATE_TRUNC('month', i.created_at) AS period,
            COUNT(DISTINCT i.id) AS invoice_count,
            SUM(i.final_amount) AS revenue,
            SUM(COALESCE(item_agg.total_cogs, 0)) AS cogs,
            SUM(i.final_amount) - SUM(COALESCE(item_agg.total_cogs, 0)) AS gross_profit
        FROM invoices i
        JOIN shifts s ON s.id = i.shift_id
        LEFT JOIN (
            SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
            FROM invoice_items
            GROUP BY invoice_id
        ) item_agg ON item_agg.invoice_id = i.id
        WHERE (CAST(:wid AS uuid) IS NULL OR s.warehouse_id = CAST(:wid AS uuid))
        AND i.type = 'SALE'
        AND i.created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC('month', i.created_at)
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportMonthly(@Param("wid")    UUID warehouseId,
                                                      @Param("from")   Instant from,
                                                      @Param("to")     Instant to);

    // Báo cáo doanh thu theo NĂM
    @Query(value = """
        SELECT
            DATE_TRUNC('year', i.created_at) AS period,
            COUNT(DISTINCT i.id) AS invoice_count,
            SUM(i.final_amount) AS revenue,
            SUM(COALESCE(item_agg.total_cogs, 0)) AS cogs,
            SUM(i.final_amount) - SUM(COALESCE(item_agg.total_cogs, 0)) AS gross_profit
        FROM invoices i
        JOIN shifts s ON s.id = i.shift_id
        LEFT JOIN (
            SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
            FROM invoice_items
            GROUP BY invoice_id
        ) item_agg ON item_agg.invoice_id = i.id
        WHERE (CAST(:wid AS uuid) IS NULL OR s.warehouse_id = CAST(:wid AS uuid))
        AND i.type = 'SALE'
        AND i.created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC('year', i.created_at)
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportYearly(@Param("wid")    UUID warehouseId,
                                                     @Param("from")   Instant from,
                                                     @Param("to")     Instant to);
}