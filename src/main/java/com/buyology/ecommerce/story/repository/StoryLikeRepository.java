package com.buyology.ecommerce.story.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import com.buyology.ecommerce.story.domain.StoryLike;

public interface StoryLikeRepository extends JpaRepository<StoryLike, UUID> {

    long countByStoryId(UUID storyId);

    boolean existsByStoryIdAndUserId(UUID storyId, UUID userId);

    @Transactional
    long deleteByStoryIdAndUserId(UUID storyId, UUID userId);
}
