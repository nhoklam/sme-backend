package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.CreateReviewRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.ProductReviewResponse;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CustomerRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.service.ProductReviewService;

import java.util.UUID;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService productReviewService;
    private final CustomerRepository customerRepository;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ProductReviewResponse>> createReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReviewRequest req) {
        UUID customerId = customerRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(productReviewService.createReview(customerId, req)));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ProductReviewResponse>> approveReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productReviewService.approveReview(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID id) {
        productReviewService.deleteReview(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa đánh giá thành công", null));
    }
}
