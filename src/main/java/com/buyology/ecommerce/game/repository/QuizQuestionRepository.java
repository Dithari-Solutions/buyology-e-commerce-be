package com.buyology.ecommerce.game.repository;

import com.buyology.ecommerce.game.domain.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findByIsActiveTrue();
}
