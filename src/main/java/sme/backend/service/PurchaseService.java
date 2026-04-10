package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreatePurchaseOrderRequest;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierDebtRepository supplierDebtRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderRequest req, UUID createdBy) {
        Supplier supplier = supplierRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.getSupplierId()));

        PurchaseOrder po = PurchaseOrder.builder()
                .code(generatePOCode())
                .supplierId(req.getSupplierId())
                .warehouseId(req.getWarehouseId())
                .createdByUserId(createdBy)
                .note(req.getNote())
                .status(PurchaseOrder.PurchaseStatus.PENDING)
                .build();

        for (CreatePurchaseOrderRequest.PurchaseItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            PurchaseItem item = PurchaseItem.builder()
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity() != null ? itemReq.getQuantity() : 0)
                    .importPrice(itemReq.getImportPrice() != null ? itemReq.getImportPrice() : BigDecimal.ZERO)
                    .build();
            po.addItem(item);
        }
        po.recalculateTotal();

        po = purchaseOrderRepository.save(po);
        log.info("Đã tạo phiếu nhập kho: {}", po.getCode());
        return po;
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(UUID poId, UUID approvedBy) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithItems(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));

        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Chỉ có thể duyệt phiếu ở trạng thái PENDING.");
        }

        try {
            po.setStatus(PurchaseOrder.PurchaseStatus.COMPLETED);
            po.setApprovedBy(approvedBy);
            po.setApprovedAt(Instant.now());
            po = purchaseOrderRepository.save(po);

            String operator = approvedBy != null ? approvedBy.toString() : "SYSTEM";

            // 1. Nhập kho từng mặt hàng an toàn
            if (po.getItems() != null) {
                for (PurchaseItem item : po.getItems()) {
                    BigDecimal importPrice = item.getImportPrice() != null ? item.getImportPrice() : BigDecimal.ZERO;
                    int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                    
                    inventoryService.importStock(
                            item.getProductId(),
                            po.getWarehouseId(),
                            quantity,
                            importPrice,
                            po.getId(), 
                            operator
                    );
                }
            } else {
                throw new BusinessException("EMPTY_ITEMS", "Phiếu nhập kho không có sản phẩm nào.");
            }

            // 2. Tạo công nợ NCC an toàn
            Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
            int paymentTerms = 30;
            if (supplier != null && supplier.getPaymentTerms() != null) {
                paymentTerms = supplier.getPaymentTerms();
            }
            
            BigDecimal totalDebt = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;

            SupplierDebt debt = SupplierDebt.builder()
                    .supplierId(po.getSupplierId())
                    .purchaseOrderId(po.getId())
                    .totalDebt(totalDebt)
                    .paidAmount(BigDecimal.ZERO)
                    .status(SupplierDebt.DebtStatus.UNPAID)
                    .dueDate(LocalDate.now().plusDays(paymentTerms))
                    .build();
            supplierDebtRepository.save(debt);

            return po;
            
        } catch (BusinessException be) {
            throw be; // Ném thẳng lỗi Business (nếu do nghiệp vụ)
        } catch (Exception e) {
            log.error("Lỗi hệ thống cực kỳ nghiêm trọng khi duyệt phiếu: ", e);
            // Gói mọi lỗi hệ thống (NullPointer, DB Constraint...) thành BusinessException để hiện lên UI
            throw new BusinessException("APPROVE_CRASH", "Lỗi xử lý hệ thống: " + e.getMessage() + " (Chi tiết: " + e.getClass().getSimpleName() + ")");
        }
    }

    @Transactional
    public PurchaseOrder cancelPurchaseOrder(UUID poId, String reason) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));

        if (po.getStatus() == PurchaseOrder.PurchaseStatus.COMPLETED) {
            throw new BusinessException("CANNOT_CANCEL", "Không thể hủy phiếu nhập kho đã hoàn thành");
        }
        po.setStatus(PurchaseOrder.PurchaseStatus.CANCELLED);
        po.setNote((po.getNote() != null ? po.getNote() + " | " : "") + "Lý do hủy: " + reason);
        return purchaseOrderRepository.save(po);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> getByWarehouse(UUID warehouseId, Pageable pageable) {
        if (warehouseId == null) return purchaseOrderRepository.findAll(pageable);
        return purchaseOrderRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> getBySupplier(UUID supplierId, Pageable pageable) {
        return purchaseOrderRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId, pageable);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getById(UUID id) {
        return purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }

    private String generatePOCode() {
        return "PO-" + System.currentTimeMillis();
    }
}