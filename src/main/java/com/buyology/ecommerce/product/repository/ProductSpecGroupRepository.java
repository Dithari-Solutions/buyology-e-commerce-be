package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductSpecGroupRepository extends JpaRepository<ProductSpecGroup, UUID> {

    List<ProductSpecGroup> findByProduct_Id(UUID productId);

    List<ProductSpecGroup> findByProduct_IdIn(List<UUID> productIds);

    /** Null the link to a global group so it can be deleted; products keep their denormalized copies. */
    @Modifying
    @Query("update ProductSpecGroup p set p.globalSpecGroup = null where p.globalSpecGroup.id = :groupId")
    void detachByGlobalGroup(@Param("groupId") UUID groupId);
}
