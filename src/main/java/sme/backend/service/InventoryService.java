package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.config.AppProperties;
import sme.backend.dto.request.AdjustInventoryRequest;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository txnRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final AppProperties appProperties;

    private int getSafeLowStockThreshold() {
        try {
            if (appProperties != null && appProperties.getBusiness() != null) {
                return appProperties.getBusiness().getLowStockThreshold();
            }
        } catch (Exception ignored) {}
        return 10;
    }

    @Transactional
    public Inventory getOrCreate(UUID productId, UUID warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> {
                    Inventory inv = Inventory.builder()
                            .productId(productId)
                            .warehouseId(warehouseId)
                            .quantity(0)
                            .reservedQuantity(0)
                            .inTransit(0)
                            .minQuantity(getSafeLowStockThreshold())
                            .build();
                    return inventoryRepository.save(inv);
                });
    }

    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void importStock(UUID productId, UUID warehouseId,
                            int quantity, java.math.BigDecimal importPrice,
                            UUID referenceId, String operator) {
        try {
            Inventory inv = getOrCreate(productId, warehouseId);
            if (inv.getMinQuantity() == null) {
                inv.setMinQuantity(getSafeLowStockThreshold());
            }
            int before = inv.getQuantity() != null ? inv.getQuantity() : 0;
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

            java.math.BigDecimal safeImportPrice = importPrice != null ? importPrice : java.math.BigDecimal.ZERO;
            try {
                product.recalculateMAC(before, quantity, safeImportPrice);
                productRepository.save(product);
            } catch (Exception e) {
                log.warn("Lỗi tính MAC: {}", e.getMessage());
            }

            inv.addQuantity(quantity);
            inv = inventoryRepository.save(inv);
            recordTransaction(inv, referenceId, "IMPORT", quantity, before, inv.getQuantity(), operator, null);
            checkLowStockAlert(inv);
        } catch (Exception e) {
            throw new BusinessException("IMPORT_STOCK_CRASH", "Lỗi nhập kho: " + e.getMessage());
        }
    }

    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void deductForPOSSale(UUID productId, UUID warehouseId,
                                 int quantity, UUID invoiceId, String operator) {
        Inventory inv = inventoryRepository.findByProductAndWarehouseWithLock(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tồn kho"));
        
        if (inv.getMinQuantity() == null) inv.setMinQuantity(getSafeLowStockThreshold());

        int before = inv.getQuantity() != null ? inv.getQuantity() : 0;
        inv.deductPhysicalQuantity(quantity);
        inv = inventoryRepository.save(inv);
        recordTransaction(inv, invoiceId, "SALE_POS", -quantity, before, inv.getQuantity(), operator, null);
        checkLowStockAlert(inv);
    }

@Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void returnToStock(UUID productId, UUID warehouseId,
                              int quantity, UUID referenceId, String reason, String operator) {
        
        Inventory inv = getOrCreate(productId, warehouseId);
        int before = inv.getQuantity() != null ? inv.getQuantity() : 0;
        
        // Kiểm tra xem đây là trả về kho bán ("STOCK") hay trả từ đơn hàng Online ("RETURNED_ORDER")
        boolean isReturnToSellable = "STOCK".equals(reason) || "RETURNED_ORDER".equals(reason);
        String txnType = isReturnToSellable ? "RETURN_TO_STOCK" : "RETURN_TO_DEFECT";

        // Chỉ cộng số lượng vào tồn kho bán nếu thỏa mãn điều kiện trên
        if (isReturnToSellable) {
            inv.addQuantity(quantity);
            
            // LƯU Ý: Nếu Entity Inventory của bạn có tách biệt giữa "Hàng thực tế" (actual) và "Hàng khả dụng" (available)
            // thì hãy bỏ comment 2 dòng dưới đây thay cho dòng inv.addQuantity() ở trên:
            // inv.setActualQuantity(inv.getActualQuantity() + quantity);
            // inv.setAvailableQuantity(inv.getAvailableQuantity() + quantity);
        }
        
        inv = inventoryRepository.save(inv);
        
        // Ghi lại lịch sử xuất nhập tồn (thẻ kho)
        recordTransaction(
                inv, 
                referenceId, 
                txnType, 
                isReturnToSellable ? quantity : 0, 
                before, 
                inv.getQuantity(), 
                operator, 
                "Hoàn trả hàng hóa - Lý do: " + reason
        );
    }

    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void reserveForOnlineOrder(UUID productId, UUID warehouseId, int quantity, UUID orderId, String operator) {
        Inventory inv = inventoryRepository.findByProductAndWarehouseWithLock(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tồn kho"));
        inv.reserveQuantity(quantity);
        inv = inventoryRepository.save(inv);
        recordTransaction(inv, orderId, "RESERVE", 0, inv.getAvailableQuantity() + quantity, inv.getAvailableQuantity(), operator, null);
    }

    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void confirmOnlineShipment(UUID productId, UUID warehouseId, int quantity, UUID orderId, String operator) {
        Inventory inv = inventoryRepository.findByProductAndWarehouseWithLock(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tồn kho"));
        if (inv.getMinQuantity() == null) inv.setMinQuantity(getSafeLowStockThreshold());
        
        int before = inv.getQuantity() != null ? inv.getQuantity() : 0;
        inv.confirmShipment(quantity);
        inv = inventoryRepository.save(inv);
        recordTransaction(inv, orderId, "SALE_ONLINE", -quantity, before, inv.getQuantity(), operator, null);
        checkLowStockAlert(inv);
    }

    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void adjustInventory(AdjustInventoryRequest req, UUID referenceId, String operator) {
        Inventory inv = getOrCreate(req.getProductId(), req.getWarehouseId());
        int before = inv.getQuantity() != null ? inv.getQuantity() : 0;
        int diff   = req.getActualQuantity() - before;

        inv.setQuantity(req.getActualQuantity());
        inv = inventoryRepository.save(inv);
        
        // ĐÃ SỬA: Đẩy lý do (reason) vào cột note, không ghép vào operator
        recordTransaction(inv, referenceId, "ADJUSTMENT", diff, before, req.getActualQuantity(), operator, req.getReason());
        checkLowStockAlert(inv);
    }

    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void releaseReservation(UUID productId, UUID warehouseId, int quantity, UUID orderId, String operator) {
        inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .ifPresent(inv -> {
                    inv.releaseReservedQuantity(quantity);
                    Inventory savedInv = inventoryRepository.save(inv);
                    recordTransaction(savedInv, orderId, "RELEASE", 0, savedInv.getAvailableQuantity(), savedInv.getAvailableQuantity(), operator, null);
                });
    }

    @Cacheable(value = "inventories", key = "#productId + '_' + #warehouseId")
    @Transactional(readOnly = true)
    public Inventory getInventory(UUID productId, UUID warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tồn kho"));
    }

    @Transactional(readOnly = true)
    public List<Inventory> getLowStockAlerts(UUID warehouseId) {
        return inventoryRepository.findLowStockByWarehouse(warehouseId);
    }

    // --- HÀM GHI THẺ KHO AN TOÀN ---
    private void recordTransaction(Inventory inv, UUID referenceId, String type,
                                   int change, int before, int after, String operator, String note) {
                                   
        // Tránh tràn cột created_by (max 100 ký tự)
        String safeOperator = (operator != null && operator.length() > 90) ? operator.substring(0, 90) : operator;
        if (safeOperator == null) safeOperator = "SYSTEM";

        InventoryTransaction txn = InventoryTransaction.builder()
                .inventoryId(inv.getId())
                .referenceId(referenceId != null ? referenceId : UUID.randomUUID()) // Chống lỗi NULL
                .transactionType(type)
                .quantityChange(change)
                .quantityBefore(before)
                .quantityAfter(after)
                .note(note) // Ghi chú dài an toàn ở cột TEXT
                .createdBy(safeOperator)
                .build();
        txnRepository.save(txn);
    }

    private void checkLowStockAlert(Inventory inv) {
        try {
            if (inv.isLowStock()) notificationService.notifyLowStock(inv);
        } catch (Exception ignored) {}
    }
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void updateMinQuantity(UUID inventoryId, int newMinQuantity, String operator) {
        if (newMinQuantity < 0) {
            throw new BusinessException("INVALID_MIN_QTY", "Định mức tồn kho tối thiểu không được nhỏ hơn 0");
        }
        
        Inventory inv = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", inventoryId));
                
        int oldMin = inv.getMinQuantity() != null ? inv.getMinQuantity() : 0;
        inv.setMinQuantity(newMinQuantity);
        inventoryRepository.save(inv);
        checkLowStockAlert(inv);
        log.info("User {} updated minQuantity for Inventory {} from {} to {}", operator, inventoryId, oldMin, newMinQuantity);
    }
}