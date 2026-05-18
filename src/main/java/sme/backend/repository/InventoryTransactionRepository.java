package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.InventoryTransaction;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    Page<InventoryTransaction> findByInventoryIdOrderByCreatedAtDesc(UUID inventoryId, Pageable pageable);

    List<InventoryTransaction> findByReferenceId(UUID referenceId);

    public interface TransactionProjection {
        UUID getId();

        UUID getInventoryId();

        UUID getReferenceId();

        String getTransactionType();

        Integer getQuantityChange();

        Integer getQuantityBefore();

        Integer getQuantityAfter();

        String getNote();

        String getCreatedBy();

        java.time.Instant getCreatedAt();

        UUID getProductId();

        UUID getWarehouseId();

        String getProductName();

        String getProductSku();

        String getProductImage();

        String getWarehouseName();
    }

    @Query(value = "SELECT t.id, t.inventory_id as inventoryId, t.reference_id as referenceId, " +
            "t.transaction_type as transactionType, t.quantity_change as quantityChange, " +
            "t.quantity_before as quantityBefore, t.quantity_after as quantityAfter, " +
            "t.note, t.created_by as createdBy, t.created_at as createdAt, " +
            "i.product_id as productId, i.warehouse_id as warehouseId, " +
            "p.name as productName, p.sku as productSku, p.image_url as productImage, " +
            "w.name as warehouseName " +
            "FROM inventory_transactions t " +
            "JOIN inventories i ON t.inventory_id = i.id " +
            "JOIN products p ON i.product_id = p.id " +
            "JOIN warehouses w ON i.warehouse_id = w.id " +
            "WHERE (:warehouseId IS NULL OR i.warehouse_id = :warehouseId) " +
            "AND (:transactionType IS NULL OR t.transaction_type = :transactionType) " +
            "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))) "
            +
            "AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR t.created_at >= :fromDate) " +
            "AND (CAST(:toDate AS TIMESTAMP) IS NULL OR t.created_at <= :toDate) " +
            "ORDER BY t.created_at DESC", countQuery = "SELECT count(t.id) " +
                    "FROM inventory_transactions t " +
                    "JOIN inventories i ON t.inventory_id = i.id " +
                    "JOIN products p ON i.product_id = p.id " +
                    "WHERE (:warehouseId IS NULL OR i.warehouse_id = :warehouseId) " +
                    "AND (:transactionType IS NULL OR t.transaction_type = :transactionType) " +
                    "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))) "
                    +
                    "AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR t.created_at >= :fromDate) " +
                    "AND (CAST(:toDate AS TIMESTAMP) IS NULL OR t.created_at <= :toDate)", nativeQuery = true)
    Page<TransactionProjection> searchGlobalTransactions(
            @Param("warehouseId") UUID warehouseId,
            @Param("transactionType") String transactionType,
            @Param("keyword") String keyword,
            @Param("fromDate") java.time.Instant fromDate,
            @Param("toDate") java.time.Instant toDate,
            Pageable pageable);
}