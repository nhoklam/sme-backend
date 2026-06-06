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
    private final sme.backend.service.OrderService orderService;

    private static final int EXPIRY_JOB_CHUNK_SIZE = 100;

    @Scheduled(fixedDelay = 900000)
    public void cancelExpiredPendingOrders() {
        log.info("Bắt đầu quét đơn hàng thanh toán quá hạn...");
        Instant expiryTime = Instant.now().minus(30, ChronoUnit.MINUTES);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, EXPIRY_JOB_CHUNK_SIZE);
        org.springframework.data.domain.Slice<Order> slice = orderRepository.findExpiredPendingOrders(expiryTime, pageable);

        while (slice.hasContent()) {
            for (Order order : slice.getContent()) {
                try {
                    orderService.cancelOrderWithCleanup(order.getId(), "Hệ thống tự hủy: quá thời gian thanh toán", "SYSTEM");
                    log.info("Auto-cancelled expired order: {}", order.getCode());
                } catch (Exception e) {
                    log.error("Lỗi khi hủy đơn hàng quá hạn {}: {}", order.getCode(), e.getMessage());
                }
            }
            if (slice.hasNext()) {
                slice = orderRepository.findExpiredPendingOrders(expiryTime, slice.nextPageable());
            } else {
                break;
            }
        }
        
        log.info("Hoàn tất quét đơn hàng thanh toán quá hạn.");
    }
}
