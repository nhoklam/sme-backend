package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sme.backend.entity.*;
import sme.backend.repository.NotificationRepository;
import sme.backend.repository.ProductRepository;
import sme.backend.repository.WarehouseRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    @Async
    public void notifyLowStock(Inventory inventory) {
        if (inventory == null || inventory.getWarehouseId() == null)
            return; // An toàn trên hết

        String topic = "/topic/warehouse/" + inventory.getWarehouseId() + "/low-stock";

        int minQty = inventory.getMinQuantity() != null ? inventory.getMinQuantity() : 0;
        int currentQty = inventory.getQuantity() != null ? inventory.getQuantity() : 0;

        // Truy vấn tên sản phẩm từ DB
        String productName = productRepository.findById(inventory.getProductId())
                .map(Product::getName)
                .orElse("Sản phẩm không xác định");

        // Truy vấn tên kho từ DB
        String warehouseName = warehouseRepository.findById(inventory.getWarehouseId())
                .map(Warehouse::getName)
                .orElse("Kho không xác định");

        // Payload đầy đủ gồm cả tên sản phẩm và tên kho
        Map<String, Object> payload = Map.of(
                "type", "LOW_STOCK",
                "productId", inventory.getProductId(),
                "warehouseId", inventory.getWarehouseId(),
                "quantity", currentQty,
                "minQuantity", minQty,
                "productName", productName,
                "warehouseName", warehouseName);
        messagingTemplate.convertAndSend(topic, payload);
        messagingTemplate.convertAndSend("/topic/admin/low-stock", payload);

        // Tạo thông báo lưu vào DB với tên đầy đủ
        Notification notification = Notification.builder()
                .type("LOW_STOCK")
                .title("⚠️ Cảnh báo tồn kho thấp")
                .message(String.format(
                        "⚠️ Cảnh báo: Sản phẩm '%s' tại kho '%s' sắp hết hàng. Hiện còn %d sản phẩm (Ngưỡng tối thiểu: %d).",
                        productName, warehouseName, currentQty, minQty))
                .payload(payload)
                .isRead(false)
                .userId(warehouseRepository.findById(inventory.getWarehouseId()).map(Warehouse::getManagerId).orElse(null))
                .build();

        notificationRepository.save(notification);
        log.info("Low stock notification saved and sent for product: {} at warehouse: {}", productName, warehouseName);
    }

    @Async
    public void notifyOutOfStock(Inventory inventory) {
        if (inventory == null || inventory.getWarehouseId() == null) return;
        
        String topic = "/topic/warehouse/" + inventory.getWarehouseId() + "/low-stock";
        String productName = productRepository.findById(inventory.getProductId()).map(Product::getName).orElse("Sản phẩm không xác định");
        String warehouseName = warehouseRepository.findById(inventory.getWarehouseId()).map(Warehouse::getName).orElse("Kho không xác định");

        Map<String, Object> payload = Map.of(
                "type", "OUT_OF_STOCK",
                "productId", inventory.getProductId(),
                "warehouseId", inventory.getWarehouseId(),
                "quantity", 0,
                "productName", productName,
                "warehouseName", warehouseName);
        
        messagingTemplate.convertAndSend(topic, payload);
        messagingTemplate.convertAndSend("/topic/admin/low-stock", payload);

        Notification notification = Notification.builder()
                .type("OUT_OF_STOCK")
                .title("🛑 Hết hàng")
                .message(String.format("🛑 Sản phẩm '%s' tại kho '%s' đã hết hàng!", productName, warehouseName))
                .payload(payload)
                .isRead(false)
                .userId(warehouseRepository.findById(inventory.getWarehouseId()).map(Warehouse::getManagerId).orElse(null))
                .build();

        notificationRepository.save(notification);
    }

    @Async
    public void notifyImportSuccess(PurchaseOrder order, UUID warehouseId) {
        String topic = "/topic/warehouse/" + warehouseId + "/inventory";
        String warehouseName = warehouseRepository.findById(warehouseId).map(Warehouse::getName).orElse("Kho không xác định");

        Map<String, Object> payload = Map.of(
                "type", "IMPORT_SUCCESS",
                "orderId", order.getId(),
                "orderCode", order.getCode(),
                "warehouseName", warehouseName);
        
        messagingTemplate.convertAndSend(topic, payload);

        Notification notification = Notification.builder()
                .type("IMPORT_SUCCESS")
                .title("✅ Nhập kho thành công")
                .message(String.format("✅ Phiếu nhập kho %s đã được nhập thành công vào kho %s.", order.getCode(), warehouseName))
                .payload(payload)
                .isRead(false)
                .userId(warehouseRepository.findById(warehouseId).map(Warehouse::getManagerId).orElse(null))
                .build();

        notificationRepository.save(notification);
    }

    @Async
    public void notifyNewOrder(Order order, UUID warehouseId) {
        String topic = "/topic/warehouse/" + warehouseId + "/new-order";

        Map<String, Object> payload = Map.of(
                "type", "NEW_ORDER",
                "orderId", order.getId(),
                "orderCode", order.getCode(),
                "amount", order.getFinalAmount() != null ? order.getFinalAmount() : 0,
                "type_order", order.getType() != null ? order.getType().name() : "DELIVERY");
        messagingTemplate.convertAndSend(topic, payload);

        Notification notification = Notification.builder()
                .type("NEW_ORDER")
                .title("🛒 Đơn hàng mới")
                .message(String.format("🛒 Khách hàng vừa đặt đơn hàng online mới: %s. Trị giá: %,.0f VNĐ.", order.getCode(), order.getFinalAmount() != null ? order.getFinalAmount() : 0))
                .payload(payload)
                .isRead(false)
                .build(); // Không set userId để cả thu ngân và quản lý đều nhận được

        notificationRepository.save(notification);
        log.debug("New order notification saved and sent: order={}", order.getCode());
    }

    @Async
    public void notifyShiftClosed(Shift shift) {
        String topic = "/topic/warehouse/" + shift.getWarehouseId() + "/shift-alert";

        Map<String, Object> payload = Map.of(
                "type", "SHIFT_PENDING_APPROVAL",
                "shiftId", shift.getId(),
                "cashierId", shift.getCashierId(),
                "discrepancyAmount", shift.getDiscrepancyAmount() != null ? shift.getDiscrepancyAmount() : 0);

        messagingTemplate.convertAndSend(topic, payload);

        Notification notification = Notification.builder()
                .type("SHIFT_PENDING_APPROVAL")
                .title("🔒 Đóng ca cần duyệt")
                .message(String.format("🔒 Ca làm việc %s có sự chênh lệch tiền mặt. Vui lòng kiểm tra và duyệt.", shift.getId()))
                .payload(payload)
                .isRead(false)
                .userId(warehouseRepository.findById(shift.getWarehouseId()).map(Warehouse::getManagerId).orElse(null))
                .build();

        notificationRepository.save(notification);
        log.debug("Shift closed notification saved and sent: shift={}", shift.getId());
    }

    @Async
    public void notifyTransferArrived(UUID transferId, UUID toWarehouseId) {
        String topic = "/topic/warehouse/" + toWarehouseId + "/transfer";

        Map<String, Object> payload = Map.of(
                "type", "TRANSFER_ARRIVED",
                "transferId", transferId);

        messagingTemplate.convertAndSend(topic, payload);

        Notification notification = Notification.builder()
                .type("TRANSFER_ARRIVED")
                .title("📦 Cập nhật trạng thái chuyển kho")
                .message("Một phiếu chuyển kho liên quan đến chi nhánh của bạn vừa được cập nhật.")
                .payload(payload)
                .build();

        notificationRepository.save(notification);
    }

    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }

    public List<Notification> getUnread(UUID userId) {
        if (userId == null) {
            return notificationRepository.findByUserIdIsNullAndIsReadFalseOrderByCreatedAtDesc();
        }
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public long countUnread(UUID userId) {
        if (userId == null) {
            return notificationRepository.countByUserIdIsNullAndIsReadFalse();
        }
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public Page<Notification> getAll(UUID userId, Pageable pageable) {
        if (userId == null) {
            return notificationRepository.findByUserIdIsNullOrderByCreatedAtDesc(pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}