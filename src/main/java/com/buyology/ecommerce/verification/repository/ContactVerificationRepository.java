package com.buyology.ecommerce.verification.repository;

import com.buyology.ecommerce.verification.domain.ContactVerification;
import com.buyology.ecommerce.verification.domain.ContactVerification.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContactVerificationRepository extends JpaRepository<ContactVerification, UUID> {

    Optional<ContactVerification> findTopByChannelAndTargetOrderByCreatedAtDesc(Channel channel, String target);

    @Modifying
    @Query("delete from ContactVerification c where c.channel = :channel and c.target = :target")
    void deleteByChannelAndTarget(@Param("channel") Channel channel, @Param("target") String target);

    @Modifying
    @Query("delete from ContactVerification c where c.expiresAt is not null and c.expiresAt < :now and c.verified = false")
    void deleteExpiredUnverified(@Param("now") Instant now);
}
