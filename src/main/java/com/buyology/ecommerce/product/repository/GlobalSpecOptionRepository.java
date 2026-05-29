package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.GlobalSpecOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GlobalSpecOptionRepository extends JpaRepository<GlobalSpecOption, UUID> {

    /** Options of a group, ordered by their configured display position. */
    List<GlobalSpecOption> findByGroupIdOrderByDisplayOrderAscCreatedAtAsc(UUID groupId);

    List<GlobalSpecOption> findByGroupId(UUID groupId);

    /** Highest display_order currently used in a group, or -1 when the group is empty. */
    @Query("select coalesce(max(o.displayOrder), -1) from GlobalSpecOption o where o.group.id = :groupId")
    int findMaxDisplayOrder(@Param("groupId") UUID groupId);
}
