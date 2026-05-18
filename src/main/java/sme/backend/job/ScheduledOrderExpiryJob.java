package sme.backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.entity.Order;
import sme.backend.entity.OrderItem;
import sme.backend.repository.OrderRepository;
import sme.backend.service.InventoryService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledOrderExpiryJob {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    @Scheduled(fixedDelay = 900000)
    @Transactional
    public void cancelExpiredPendingOrders() {
        log.info("Bắt đầu quét đơn hàng thanh toán quá hạn...");
        Instant expiryTime = Instant.now().minus(30, ChronoUnit.MINUTES);

        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(expiryTime);

        for (Order order : expiredOrders) {
            try {
                // 1. Giải phóng tồn kho cho từng item
                for (OrderItem item : order.getItems()) {
                    if (order.getAssignedWarehouseId() != null) {
                        inventoryService.releaseReservation(item.getProductId(), order.getAssignedWarehouseId(),
                                item.getQuantity(), order.getId(), "SYSTEM");
                    }
                }

                order.transitionTo(Order.OrderStatus.CANCELLED, "Hệ thống tự hủy: quá thời gian thanh toán", "SYSTEM");
                orderRepository.save(order);

                log.info("Auto-cancelled expired order: {}", order.getCode());
            } catch (Exception e) {
                log.error("Lỗi khi hủy đơn hàng quá hạn {}: {}", order.getCode(), e.getMessage());
            }
        }
        log.info("Hoàn tất quét đơn hàng thanh toán quá hạn.");
    }
}
