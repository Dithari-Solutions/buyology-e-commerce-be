package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductSpecOptionRepository extends JpaRepository<ProductSpecOption, UUID> {

    List<ProductSpecOption> findByGroup_Id(UUID groupId);

    List<ProductSpecOption> findByGroup_IdIn(List<UUID> groupIds);

    /**
     * Returns distinct non-blank values for a spec group code across all active products.
     * Used to populate dynamic filter options on the catalog filter panel.
     */
    @Query("""
            SELECT DISTINCT o.value FROM ProductSpecOption o
            JOIN o.group g
            JOIN g.product p
            WHERE g.code = :code
              AND p.status = 'ACTIVE'
              AND o.value IS NOT NULL
              AND o.value <> ''
            ORDER BY o.value
            """)
    List<String> findDistinctValuesBySpecGroupCode(@Param("code") String code);

    /**
     * Like {@link #findDistinctValuesBySpecGroupCode} but also returns each value's unit
     * (from the linked GlobalSpecOption) so filters can show "8 GB" rather than "8".
     * Each row is [String value, SpecUnit unit] (unit may be null).
     */
    @Query("""
            SELECT DISTINCT o.value, gso.unit FROM ProductSpecOption o
            JOIN o.group g
            JOIN g.product p
            LEFT JOIN o.globalSpecOption gso
            WHERE g.code = :code
              AND p.status = 'ACTIVE'
              AND o.value IS NOT NULL
              AND o.value <> ''
            ORDER BY o.value
            """)
    List<Object[]> findDistinctValueUnitsBySpecGroupCode(@Param("code") String code);
}
