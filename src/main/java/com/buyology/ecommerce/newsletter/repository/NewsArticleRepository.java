package com.buyology.ecommerce.newsletter.repository;

import com.buyology.ecommerce.newsletter.domain.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID> {
    List<NewsArticle> findAllByStatusOrderByPublishedAtDesc(NewsArticle.ArticleStatus status);

    Optional<NewsArticle> findBySlug(String slug);

    long countByStatusAndPublishedAtAfter(NewsArticle.ArticleStatus status, java.time.Instant since);
    List<NewsArticle> findAllByOrderByCreatedAtDesc();
}
