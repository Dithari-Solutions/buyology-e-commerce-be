package com.buyology.ecommerce.refund.repository;

import com.buyology.ecommerce.refund.domain.RefundSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefundSettingRepository extends JpaRepository<RefundSetting, UUID> {
}
