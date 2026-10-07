package com.buyology.ecommerce.story.repository;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import com.buyology.ecommerce.story.domain.StoryLike;

public interface StoryLikeRepository extends JpaRepository<StoryLike, UUID> {

    @Query("select e.storyId as storyId, count(e) as total from StoryLike e where e.storyId in :ids group by e.storyId")
    List<StoryEngagementCount> countsForStories(@Param("ids") List<UUID> ids);


    @Query("select e.storyId from StoryLike e where e.userId = :userId and e.storyId in :ids")
    List<UUID> likedStoryIds(@Param("userId") UUID userId, @Param("ids") List<UUID> ids);

    long countByStoryId(UUID storyId);

    boolean existsByStoryIdAndUserId(UUID storyId, UUID userId);

    @Transactional
    long deleteByStoryIdAndUserId(UUID storyId, UUID userId);
}
