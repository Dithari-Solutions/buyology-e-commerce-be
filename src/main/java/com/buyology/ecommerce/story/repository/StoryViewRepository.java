package com.buyology.ecommerce.story.repository;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.buyology.ecommerce.story.domain.StoryView;

public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

    @Query("select e.storyId as storyId, count(e) as total from StoryView e where e.storyId in :ids group by e.storyId")
    List<StoryEngagementCount> countsForStories(@Param("ids") List<UUID> ids);


    long countByStoryId(UUID storyId);

    boolean existsByStoryIdAndUserId(UUID storyId, UUID userId);

    boolean existsByStoryIdAndViewerHash(UUID storyId, String viewerHash);
}
