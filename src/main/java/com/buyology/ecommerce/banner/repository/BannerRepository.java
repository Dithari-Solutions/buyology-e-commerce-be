package com.buyology.ecommerce.banner.repository;

import com.buyology.ecommerce.banner.domain.Banner;
import com.buyology.ecommerce.banner.domain.BannerPlatform;
import com.buyology.ecommerce.banner.domain.BannerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BannerRepository extends JpaRepository<Banner, UUID> {

    List<Banner> findByStatusAndPlatformOrderBySortOrderAscCreatedAtDesc(
            BannerStatus status, BannerPlatform platform);

    List<Banner> findAllByOrderBySortOrderAscCreatedAtDesc();

    List<Banner> findByPlatformOrderBySortOrderAscCreatedAtDesc(BannerPlatform platform);
}
