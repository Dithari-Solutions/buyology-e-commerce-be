package com.buyology.ecommerce.story.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.domain.*;
import com.buyology.ecommerce.story.repository.*;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoryFeedServiceTest {
    @Test void sharesPublicDataButComputesEachAccountsLikesSeparately() {
        var stories = mock(StoryRepository.class);
        var views = mock(StoryViewRepository.class);
        var likes = mock(StoryLikeRepository.class);
        var service = new StoryService(stories, mock(StoryMediaRepository.class), views, likes,
                mock(StoryEngagementWriter.class), mock(ContaboObjectService.class));
        UUID id = UUID.randomUUID(), userA = UUID.randomUUID(), userB = UUID.randomUUID();
        Story story = new Story(userA); org.springframework.test.util.ReflectionTestUtils.setField(story, "id", id);
        StoryTranslation translation = new StoryTranslation(); translation.setLanguage(Language.EN); translation.setTitle("Cached story"); story.addTranslation(translation);
        when(stories.findByStatusOrderByDisplayOrderAscCreatedAtDesc(StoryStatus.ACTIVE)).thenReturn(List.of(story));
        when(views.countsForStories(List.of(id))).thenReturn(List.of());
        when(likes.countsForStories(List.of(id))).thenReturn(List.of());
        when(likes.likedStoryIds(userA, List.of(id))).thenReturn(List.of(id));
        when(likes.likedStoryIds(userB, List.of(id))).thenReturn(List.of());
        var a = service.getPublicStories(Language.EN, userA);
        var b = service.getPublicStories(Language.EN, userB);
        var guest = service.getPublicStories(Language.EN, null);
        assertTrue(a.getBody().getData().get(0).likedByMe());
        assertFalse(b.getBody().getData().get(0).likedByMe());
        assertFalse(guest.getBody().getData().get(0).likedByMe());
        assertEquals("private, no-store", a.getHeaders().getFirst("Cache-Control"));
        assertEquals("public, max-age=30", guest.getHeaders().getFirst("Cache-Control"));
        assertEquals("Authorization", guest.getHeaders().getFirst("Vary"));
        verify(stories, times(1)).findByStatusOrderByDisplayOrderAscCreatedAtDesc(StoryStatus.ACTIVE);
        verify(views, never()).countByStoryId(any());
        verify(likes, never()).countByStoryId(any());
    }
    @Test void publishingChangesInvalidateOnlyAfterCommit() {
        var stories = mock(StoryRepository.class);
        var service = new StoryService(stories, mock(StoryMediaRepository.class), mock(StoryViewRepository.class),
                mock(StoryLikeRepository.class), mock(StoryEngagementWriter.class), mock(ContaboObjectService.class));
        when(stories.findByStatusOrderByDisplayOrderAscCreatedAtDesc(StoryStatus.ACTIVE)).thenReturn(List.of());
        UUID id = UUID.randomUUID();
        when(stories.findById(id)).thenReturn(java.util.Optional.of(new Story(UUID.randomUUID())));
        service.getPublicStories(Language.EN, null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateDisplayOrder(id, 1);
            service.getPublicStories(Language.EN, null);
            verify(stories, times(1)).findByStatusOrderByDisplayOrderAscCreatedAtDesc(StoryStatus.ACTIVE);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            service.getPublicStories(Language.EN, null);
            verify(stories, times(2)).findByStatusOrderByDisplayOrderAscCreatedAtDesc(StoryStatus.ACTIVE);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
