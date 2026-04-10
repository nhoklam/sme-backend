package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // POS: Quét mã vạch → tìm sản phẩm
    Optional<Product> findByIsbnBarcodeAndIsActiveTrue(String isbnBarcode);

    Optional<Product> findBySkuAndIsActiveTrue(String sku);

    boolean existsByIsbnBarcode(String isbnBarcode);

    // Tìm kiếm full-text (cho POS search bar)
    @Query("""
        SELECT p FROM Product p
        WHERE p.isActive = true
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR p.isbnBarcode LIKE CONCAT('%', :keyword, '%')
          OR p.sku LIKE CONCAT('%', :keyword, '%'))
        ORDER BY p.name
        """)
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Lọc theo danh mục
    Page<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId, Pageable pageable);

    // Cập nhật MAC price sau nhập kho
    @Modifying
    @Query("UPDATE Product p SET p.macPrice = :macPrice WHERE p.id = :id")
    void updateMacPrice(@Param("id") UUID id, @Param("macPrice") BigDecimal macPrice);

    // Products cần vector hóa AI (chưa có embedding)
    @Query(value = """
        SELECT p.* FROM products p
        WHERE p.is_active = true
        AND NOT EXISTS (
            SELECT 1 FROM product_vectors pv WHERE pv.product_id = p.id
        )
        """, nativeQuery = true)
    List<Product> findProductsWithoutEmbedding();

    // ĐÃ SỬA: Đổi kiểu trả về thành List<Map<String, Object>>
    // Báo cáo: top sản phẩm bán chạy theo invoices
    @Query(value = """
        SELECT p.id as id, p.name as name, SUM(ii.quantity) as total_sold
        FROM products p
        JOIN invoice_items ii ON p.id = ii.product_id
        JOIN invoices i ON ii.invoice_id = i.id
        JOIN shifts s ON i.shift_id = s.id
        WHERE i.created_at >= :fromDate AND i.created_at <= :toDate
        AND (CAST(:warehouseId AS VARCHAR) IS NULL OR CAST(s.warehouse_id AS VARCHAR) = CAST(:warehouseId AS VARCHAR))
        GROUP BY p.id, p.name
        ORDER BY total_sold DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Map<String, Object>> findTopSellingProducts(
            @Param("warehouseId") UUID warehouseId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("limit") int limit);
            
/// ĐÃ SỬA: Xóa bỏ ép kiểu CAST gây lỗi 500 khi categoryId là null
    @Query("""
            SELECT p FROM Product p
            WHERE (:categoryId IS NULL OR p.categoryId = :categoryId)
            AND (:isActive IS NULL OR p.isActive = :isActive)
            AND (:keyword IS NULL OR :keyword = ''
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR p.isbnBarcode LIKE CONCAT('%', :keyword, '%')
            OR p.sku LIKE CONCAT('%', :keyword, '%'))
            ORDER BY p.name
            """)
        Page<Product> searchProducts(@Param("keyword") String keyword, 
                                    @Param("categoryId") UUID categoryId, 
                                    @Param("isActive") Boolean isActive,
                                    Pageable pageable);
}