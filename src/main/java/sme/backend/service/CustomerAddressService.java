package sme.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CustomerAddressRequest;
import sme.backend.entity.CustomerAddress;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CustomerAddressRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerAddressRepository customerAddressRepository;

    @Transactional(readOnly = true)
    public List<CustomerAddress> getAddresses(UUID customerId) {
        return customerAddressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customerId);
    }

    @Transactional
    public CustomerAddress addAddress(UUID customerId, CustomerAddressRequest req) {
        long count = customerAddressRepository.countByCustomerId(customerId);
        if (count >= 10) {
            throw new BusinessException("ADDRESS_LIMIT_EXCEEDED", "Khách hàng chỉ được lưu tối đa 10 địa chỉ");
        }

        boolean isDefault = req.getIsDefault() != null ? req.getIsDefault() : false;
        if (count == 0) {
            isDefault = true; // Địa chỉ đầu tiên luôn là mặc định
        }

        if (isDefault) {
            clearDefaultAddresses(customerId);
        }

        CustomerAddress address = CustomerAddress.builder()
                .customerId(customerId)
                .receiverName(req.getReceiverName())
                .receiverPhone(req.getReceiverPhone())
                .provinceCity(req.getProvinceCity())
                .district(req.getDistrict())
                .ward(req.getWard())
                .specificAddress(req.getSpecificAddress())
                .isDefault(isDefault)
                .build();

        return customerAddressRepository.save(address);
    }

    @Transactional
    public CustomerAddress updateAddress(UUID customerId, UUID addressId, CustomerAddressRequest req) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", addressId));

        if (!address.getCustomerId().equals(customerId)) {
            throw new BusinessException("UNAUTHORIZED", "Địa chỉ không thuộc về khách hàng này");
        }

        boolean isDefault = req.getIsDefault() != null ? req.getIsDefault() : address.getIsDefault();
        if (isDefault && !address.getIsDefault()) {
            clearDefaultAddresses(customerId);
        }

        address.setReceiverName(req.getReceiverName());
        address.setReceiverPhone(req.getReceiverPhone());
        address.setProvinceCity(req.getProvinceCity());
        address.setDistrict(req.getDistrict());
        address.setWard(req.getWard());
        address.setSpecificAddress(req.getSpecificAddress());
        address.setIsDefault(isDefault);

        return customerAddressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID customerId, UUID addressId) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", addressId));

        if (!address.getCustomerId().equals(customerId)) {
            throw new BusinessException("UNAUTHORIZED", "Địa chỉ không thuộc về khách hàng này");
        }

        customerAddressRepository.delete(address);

        if (address.getIsDefault()) {
            List<CustomerAddress> remaining = customerAddressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customerId);
            if (!remaining.isEmpty()) {
                CustomerAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                customerAddressRepository.save(newDefault);
            }
        }
    }

    @Transactional
    public void setDefault(UUID customerId, UUID addressId) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", addressId));

        if (!address.getCustomerId().equals(customerId)) {
            throw new BusinessException("UNAUTHORIZED", "Địa chỉ không thuộc về khách hàng này");
        }

        clearDefaultAddresses(customerId);
        address.setIsDefault(true);
        customerAddressRepository.save(address);
    }

    private void clearDefaultAddresses(UUID customerId) {
        customerAddressRepository.findByCustomerIdAndIsDefaultTrue(customerId).ifPresent(addr -> {
            addr.setIsDefault(false);
            customerAddressRepository.save(addr);
        });
    }
}
