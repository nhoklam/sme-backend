package sme.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.response.ProductResponse;
import sme.backend.dto.response.WishlistItemResponse;
import sme.backend.entity.Product;
import sme.backend.entity.Wishlist;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.ProductRepository;
import sme.backend.repository.WishlistRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(UUID customerId) {
        List<Wishlist> wishlists = wishlistRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return wishlists.stream().map(w -> {
            Product product = productRepository.findById(w.getProductId())
                    .orElse(null);
            ProductResponse productResponse = null;
            if (product != null) {
                // Sử dụng hàm có sẵn của productService để map kèm tồn kho và hình ảnh
                productResponse = productService.mapToResponse(product, null); // Có thể load tồn kho nếu cần
            }
            return WishlistItemResponse.builder()
                    .wishlistId(w.getId())
                    .product(productResponse)
                    .addedAt(w.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addToWishlist(UUID customerId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (!product.getIsActive()) {
            throw new BusinessException("PRODUCT_INACTIVE", "Sản phẩm đã ngừng kinh doanh");
        }

        if (wishlistRepository.existsByCustomerIdAndProductId(customerId, productId)) {
            return; // Đã có rồi thì bỏ qua
        }

        long count = wishlistRepository.countByProductId(customerId);
        if (count >= 100) {
            throw new BusinessException("WISHLIST_LIMIT_EXCEEDED", "Danh sách yêu thích chỉ chứa tối đa 100 sản phẩm");
        }

        Wishlist wishlist = Wishlist.builder()
                .customerId(customerId)
                .productId(productId)
                .build();
        wishlistRepository.save(wishlist);
    }

    @Transactional
    public void removeFromWishlist(UUID customerId, UUID productId) {
        wishlistRepository.deleteByCustomerIdAndProductId(customerId, productId);
    }

    @Transactional(readOnly = true)
    public boolean isInWishlist(UUID customerId, UUID productId) {
        return wishlistRepository.existsByCustomerIdAndProductId(customerId, productId);
    }
}
