package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreateProductRequest;
import sme.backend.dto.request.UpdateProductRequest;
import sme.backend.dto.response.ProductResponse;
import sme.backend.entity.Category;
import sme.backend.entity.Product;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CategoryRepository;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.ProductRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest req) {
        if (productRepository.existsByIsbnBarcode(req.getIsbnBarcode())) {
            throw new BusinessException("DUPLICATE_BARCODE",
                    "Mã vạch/ISBN '" + req.getIsbnBarcode() + "' đã tồn tại");
        }
        categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        Product product = Product.builder()
                .categoryId(req.getCategoryId())
                .supplierId(req.getSupplierId()) // Lưu NCC
                .isbnBarcode(req.getIsbnBarcode())
                .sku(req.getSku())
                .name(req.getName())
                .description(req.getDescription())
                .retailPrice(req.getRetailPrice())
                .wholesalePrice(req.getWholesalePrice())
                .imageUrl(req.getImageUrl())
                .unit(req.getUnit() != null ? req.getUnit() : "Cuốn")
                .weight(req.getWeight())
                .isActive(true)
                .build();

        product = productRepository.save(product);
        log.info("Product created: {} | ISBN: {}", product.getName(), product.getIsbnBarcode());
        return mapToResponse(product, 0); // Sản phẩm mới tạo, tồn kho = 0
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(UUID id, UpdateProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (req.getName() != null)          product.setName(req.getName());
        if (req.getDescription() != null)   product.setDescription(req.getDescription());
        if (req.getRetailPrice() != null)   product.setRetailPrice(req.getRetailPrice());
        if (req.getWholesalePrice() != null) product.setWholesalePrice(req.getWholesalePrice());
        if (req.getImageUrl() != null)      product.setImageUrl(req.getImageUrl());
        if (req.getCategoryId() != null)    product.setCategoryId(req.getCategoryId());
        if (req.getIsActive() != null)      product.setIsActive(req.getIsActive());
        
        // ĐÃ BỔ SUNG LOGIC SỬA NHÀ CUNG CẤP TẠI ĐÂY
        if (req.getHasSupplierId() != null && req.getHasSupplierId()) {
            product.setSupplierId(req.getSupplierId());
        }

        Product savedProduct = productRepository.save(product);
        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(id);
        return mapToResponse(savedProduct, availableQty);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#barcode")
    public ProductResponse getByBarcode(String barcode, UUID warehouseId) {
        Product product = productRepository.findByIsbnBarcodeAndIsActiveTrue(barcode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy sản phẩm với mã vạch: " + barcode));

        Integer available = null;
        if (warehouseId != null) {
            available = inventoryRepository
                    .findByProductIdAndWarehouseId(product.getId(), warehouseId)
                    .map(inv -> inv.getAvailableQuantity())
                    .orElse(0);

            if (available <= 0) {
                throw new BusinessException("OUT_OF_STOCK",
                        "Sản phẩm '" + product.getName() + "' đã hết hàng tại chi nhánh này");
            }
        } else {
             available = inventoryRepository.getTotalAvailableQuantity(product.getId());
        }
        return mapToResponse(product, available);
    }

    // ĐÃ SỬA: Thêm tham số isActive và áp dụng Bulk Fetch để tránh N+1 Query
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String keyword, UUID categoryId, Boolean isActive, Pageable pageable) {
        Page<Product> productPage = productRepository.searchProducts(keyword, categoryId, isActive, pageable);

        if (productPage.isEmpty()) {
            return productPage.map(p -> mapToResponse(p, 0));
        }

        // 1. Tối ưu N+1: Gom tất cả categoryId và fetch 1 lần
        List<UUID> categoryIds = productPage.getContent().stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
                
        Map<UUID, String> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        // 2. Tối ưu N+1: Gom tất cả productId và fetch tồn kho 1 lần
        List<UUID> productIds = productPage.getContent().stream()
                .map(Product::getId)
                .toList();
                
        List<Object[]> bulkInventory = inventoryRepository.getBulkTotalAvailableQuantity(productIds);
        Map<UUID, Integer> inventoryMap = bulkInventory.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        // 3. Map sang Response mà không tốn thêm Query nào xuống DB
        return productPage.map(p -> {
            String catName = categoryMap.getOrDefault(p.getCategoryId(), "Chưa phân loại");
            Integer availableQty = inventoryMap.getOrDefault(p.getId(), 0);
            
            return ProductResponse.builder()
                .id(p.getId())
                .categoryId(p.getCategoryId())
                .categoryName(catName)
                .supplierId(p.getSupplierId())
                .isbnBarcode(p.getIsbnBarcode())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .retailPrice(p.getRetailPrice())
                .wholesalePrice(p.getWholesalePrice())
                .macPrice(p.getMacPrice())
                .imageUrl(p.getImageUrl())
                .unit(p.getUnit())
                .weight(p.getWeight())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .availableQuantity(availableQty)
                .build();
        });
    }

    // ĐÃ SỬA: Lấy tồn kho thực tế thay vì truyền null
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(p.getId());
        return mapToResponse(p, availableQty);
    }

    public ProductResponse mapToResponse(Product p, Integer availableQty) {
        String catName = categoryRepository.findById(p.getCategoryId())
                .map(Category::getName).orElse("Chưa phân loại");

        return ProductResponse.builder()
                .id(p.getId())
                .categoryId(p.getCategoryId())
                .categoryName(catName)
                .supplierId(p.getSupplierId()) // Map ID NCC ra ngoài
                .isbnBarcode(p.getIsbnBarcode())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .retailPrice(p.getRetailPrice())
                .wholesalePrice(p.getWholesalePrice())
                .macPrice(p.getMacPrice())
                .imageUrl(p.getImageUrl())
                .unit(p.getUnit())
                .weight(p.getWeight())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .availableQuantity(availableQty)
                .build();
    }
}