package com.buyology.ecommerce.newsletter.repository;

import com.buyology.ecommerce.newsletter.domain.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID> {
    Optional<NewsletterSubscriber> findByEmailIgnoreCase(String email);
    Optional<NewsletterSubscriber> findByUnsubscribeToken(UUID token);
    List<NewsletterSubscriber> findAllByIsActiveTrue();
    long countByIsActiveTrue();
}
