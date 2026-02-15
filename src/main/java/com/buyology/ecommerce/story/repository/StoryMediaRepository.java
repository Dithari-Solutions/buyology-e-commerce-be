package com.buyology.ecommerce.story.repository;

import com.buyology.ecommerce.story.domain.StoryMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoryMediaRepository extends JpaRepository<StoryMedia, UUID> {
}
