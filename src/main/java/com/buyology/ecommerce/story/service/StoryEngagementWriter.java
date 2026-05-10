package com.buyology.ecommerce.story.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.buyology.ecommerce.story.domain.StoryLike;
import com.buyology.ecommerce.story.domain.StoryView;
import com.buyology.ecommerce.story.repository.StoryLikeRepository;
import com.buyology.ecommerce.story.repository.StoryViewRepository;

/**
 * Unique-row inserts in their own physical transaction. A duplicate-key
 * failure here rolls back only this inner transaction, leaving the caller's
 * transaction usable (Postgres otherwise aborts the whole connection on a
 * constraint violation).
 */
@Component
public class StoryEngagementWriter {

    private final StoryViewRepository storyViewRepository;
    private final StoryLikeRepository storyLikeRepository;

    public StoryEngagementWriter(StoryViewRepository storyViewRepository,
                                 StoryLikeRepository storyLikeRepository) {
        this.storyViewRepository = storyViewRepository;
        this.storyLikeRepository = storyLikeRepository;
    }

    /** Returns true if a new row was inserted, false if it already existed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insertViewIfAbsent(UUID storyId, UUID userId, String viewerHash) {
        try {
            storyViewRepository.saveAndFlush(new StoryView(storyId, userId, viewerHash));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /** Returns true if a new like was inserted, false if it already existed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insertLikeIfAbsent(UUID storyId, UUID userId) {
        try {
            storyLikeRepository.saveAndFlush(new StoryLike(storyId, userId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
