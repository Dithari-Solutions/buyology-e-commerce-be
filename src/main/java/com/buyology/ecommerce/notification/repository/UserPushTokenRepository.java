package com.buyology.ecommerce.notification.repository;

import com.buyology.ecommerce.notification.domain.UserPushToken;
import com.buyology.ecommerce.notification.domain.UserPushToken.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, UUID> {

    List<UserPushToken> findByUserId(UUID userId);

    Optional<UserPushToken> findByUserIdAndDeviceType(UUID userId, DeviceType deviceType);

    void deleteByFcmToken(String fcmToken);
}
