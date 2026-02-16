package com.buyology.ecommerce.story.repository;

import java.util.List;
import java.util.UUID;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.domain.StoryStatus;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buyology.ecommerce.story.dto.StorySummaryResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    // @Query("""
    //             SELECT new com.buyology.ecommerce.story.dto.StorySummaryResponse(
    //                 t.title,
    //                 m.url
    //             )
    //             FROM Story s
    //             JOIN s.translations t
    //             JOIN s.media m
    //             WHERE s.status = 'ACTIVE'
    //               AND t.language = :language
    //               AND m.url IS NOT NULL
    //             ORDER BY s.startAt DESC
    //         """)
    // List<StorySummaryResponse> findAllStorySummaries(@Param("language") Language language);

    List<Story> findByStatus(StoryStatus status);

}
