package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Promotion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

  Optional<Promotion> findByCode(String code);

  boolean existsByCode(String code);

  @Query("""
      SELECT p FROM Promotion p
      WHERE p.isActive = true
        AND p.startDate <= :now
        AND p.endDate >= :now
        AND (p.maxUsage IS NULL OR p.usedCount < p.maxUsage)
      ORDER BY p.createdAt DESC
      """)
  List<Promotion> findActivePromotions(@Param("now") Instant now);

  @Query(value = "SELECT * FROM promotions " +
      "WHERE (CAST(:keyword AS VARCHAR) IS NULL " +
      "   OR LOWER(code) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')) " +
      "   OR LOWER(name) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))) " +
      "ORDER BY created_at DESC", countQuery = "SELECT COUNT(*) FROM promotions " +
          "WHERE (CAST(:keyword AS VARCHAR) IS NULL " +
          "   OR LOWER(code) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')) " +
          "   OR LOWER(name) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))", nativeQuery = true)
  Page<Promotion> searchPromotions(@Param("keyword") String keyword, Pageable pageable);
}
