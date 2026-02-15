package com.buyology.ecommerce.story.repository;

import com.buyology.ecommerce.story.domain.Story;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {
}
