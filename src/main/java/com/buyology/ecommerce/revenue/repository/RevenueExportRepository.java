package com.buyology.ecommerce.revenue.repository;

import com.buyology.ecommerce.revenue.domain.RevenueExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RevenueExportRepository extends JpaRepository<RevenueExport, UUID> {
    List<RevenueExport> findAllByOrderByCreatedAtDesc();
}
