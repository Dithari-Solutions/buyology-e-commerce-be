package com.buyology.ecommerce.story.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.buyology.ecommerce.story.domain.StoryView;

public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

    long countByStoryId(UUID storyId);

    boolean existsByStoryIdAndUserId(UUID storyId, UUID userId);

    boolean existsByStoryIdAndViewerHash(UUID storyId, String viewerHash);
}
