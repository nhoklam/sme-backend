package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sme.backend.entity.CustomerAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(UUID customerId);

    Optional<CustomerAddress> findByCustomerIdAndIsDefaultTrue(UUID customerId);

    long countByCustomerId(UUID customerId);
}
