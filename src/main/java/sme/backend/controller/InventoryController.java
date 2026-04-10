package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.AdjustInventoryRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.LowStockItem;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.Inventory;
import sme.backend.entity.User;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.InventoryTransactionRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.service.InventoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository txnRepository;

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<List<Inventory>>> getByWarehouse(
            @PathVariable UUID warehouseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID wid = (principal.getRole() == User.UserRole.ROLE_ADMIN) ? warehouseId : principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryRepository.findByWarehouseId(wid)));
    }

    @GetMapping("/{productId}/warehouse/{warehouseId}")
    public ResponseEntity<ApiResponse<Inventory>> getOne(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventory(productId, warehouseId)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<LowStockItem>>> getLowStock(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId) {
        UUID wid = (principal.getRole() == User.UserRole.ROLE_ADMIN) && warehouseId != null ? warehouseId : principal.getWarehouseId();
        List<LowStockItem> result = inventoryRepository.findLowStockWithNameByWarehouse(wid);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // -------------------------------------------------------------
    // ĐÃ SỬA: Bổ sung phân trang cho API lấy lịch sử giao dịch (Thẻ kho)
    // -------------------------------------------------------------
    @GetMapping("/{inventoryId}/transactions")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<?>>> getTransactions(
            @PathVariable UUID inventoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<?> txns = txnRepository.findByInventoryIdOrderByCreatedAtDesc(inventoryId, pageable);
        
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of((Page) txns)));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> adjustInventory(
            @Valid @RequestBody AdjustInventoryRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        try {
            inventoryService.adjustInventory(req, UUID.randomUUID(), principal.getUsername());
            return ResponseEntity.ok(ApiResponse.ok("Điều chỉnh tồn kho thành công", null));
        } catch (Exception e) {
            log.error("LỖI NGHIÊM TRỌNG KHI KIỂM KÊ: ", e);
            throw new sme.backend.exception.BusinessException("ADJUST_ERROR", "Lỗi Backend: " + e.getMessage());
        }
    }

    @PutMapping("/{inventoryId}/min-quantity")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> updateMinQuantity(
            @PathVariable UUID inventoryId,
            @RequestBody java.util.Map<String, Integer> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Integer minQty = body.get("minQuantity");
        if (minQty == null) {
            throw new sme.backend.exception.BusinessException("INVALID_INPUT", "Vui lòng cung cấp minQuantity");
        }
        inventoryService.updateMinQuantity(inventoryId, minQty, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật định mức an toàn thành công", null));
    }
}