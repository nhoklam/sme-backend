package sme.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.entity.Customer;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CustomerRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Import hàng loạt khách hàng từ Excel
     * Bỏ qua các khách hàng có số điện thoại đã tồn tại
     */
    @Transactional
    public int importBulkCustomers(List<Customer> requests) {
        int importedCount = 0;
        for (Customer req : requests) {
            // Chỉ lưu nếu Số điện thoại chưa tồn tại trong hệ thống (chống trùng lặp)
            if (!customerRepository.existsByPhoneNumber(req.getPhoneNumber())) {
                Customer customer = Customer.builder()
                        .fullName(req.getFullName())
                        .phoneNumber(req.getPhoneNumber())
                        .email(req.getEmail())
                        .address(req.getAddress())
                        .gender(req.getGender() != null ? req.getGender() : "OTHER")
                        .loyaltyPoints(0)
                        .totalSpent(BigDecimal.ZERO)
                        .customerTier(Customer.CustomerTier.STANDARD)
                        .isActive(true)
                        .notes(req.getNotes())
                        .build();
                customerRepository.save(customer);
                importedCount++;
            }
        }
        return importedCount;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "customers", key = "#id.toString()")
    public Customer getById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "customers", key = "#phone")
    public Customer getByPhone(String phone) {
        return customerRepository.findByPhoneNumberAndIsActiveTrue(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với SĐT: " + phone));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "customers", key = "#customer.id.toString()", condition = "#customer.id != null"),
            @CacheEvict(value = "customers", key = "#customer.phoneNumber", condition = "#customer.phoneNumber != null")
    })
    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }
}