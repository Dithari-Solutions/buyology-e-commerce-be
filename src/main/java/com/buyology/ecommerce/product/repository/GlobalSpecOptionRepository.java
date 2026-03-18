package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.GlobalSpecOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GlobalSpecOptionRepository extends JpaRepository<GlobalSpecOption, UUID> {

    List<GlobalSpecOption> findByGroupId(UUID groupId);
}
