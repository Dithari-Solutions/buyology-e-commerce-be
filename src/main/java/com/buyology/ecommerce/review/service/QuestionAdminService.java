package com.buyology.ecommerce.review.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.domain.ProductQuestion;
import com.buyology.ecommerce.review.domain.ProductQuestionAnswer;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.repository.ProductQuestionAnswerRepository;
import com.buyology.ecommerce.review.repository.ProductQuestionRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionAdminService {

    private final ProductQuestionRepository questionRepository;
    private final ProductQuestionAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final QuestionService questionService;

    public QuestionAdminService(ProductQuestionRepository questionRepository,
                                ProductQuestionAnswerRepository answerRepository,
                                UserRepository userRepository,
                                QuestionService questionService) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
        this.questionService = questionService;
    }

    // ── List all questions (admin view, filterable by status) ─────────────────

    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getAllQuestions(
            ModerationStatus status, int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductQuestion> questions = (status != null)
                ? questionRepository.findByStatusAndDeletedAtIsNull(status, pageable)
                : questionRepository.findByDeletedAtIsNull(pageable);

        List<QuestionResponse> data = questions.getContent().stream()
                .map(q -> questionService.toResponse(q, true))
                .toList();
        return ApiResponse.success(data, "Questions fetched successfully");
    }

    // ── Get single question by ID (admin view) ────────────────────────────────

    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestionById(UUID questionId) {
        ProductQuestion question = questionRepository.findById(questionId).orElse(null);
        if (question == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }
        return ApiResponse.success(questionService.toResponse(question, true), "Question fetched successfully");
    }

    // ── Moderate a question (approve / reject) ────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<QuestionResponse>> moderateQuestion(
            UUID questionId, ModerateQuestionRequest request) {

        ProductQuestion question = questionRepository.findById(questionId).orElse(null);
        if (question == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }

        question.setStatus(request.getStatus());
        question.setModeratedBy(request.getModeratedBy());
        question.setModeratedAt(Instant.now());

        ProductQuestion saved = questionRepository.save(question);
        return ApiResponse.success(questionService.toResponse(saved, true), "Question moderated successfully");
    }

    // ── Admin delete question ─────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(UUID questionId) {
        ProductQuestion question = questionRepository.findById(questionId).orElse(null);
        if (question == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }
        question.setDeletedAt(Instant.now());
        questionRepository.save(question);
        return ApiResponse.success(null, "Question deleted successfully");
    }

    // ── Add answer to a question ──────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<QuestionResponse>> addAnswer(
            UUID questionId, CreateQuestionAnswerRequest request) {

        ProductQuestion question = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElse(null);
        if (question == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }

        if (answerRepository.existsByQuestionIdAndDeletedAtIsNull(questionId)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "An answer already exists for this question. Use PUT to update it.");
        }

        Users admin = userRepository.findById(request.getAdminId()).orElse(null);
        if (admin == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Admin user not found");
        }

        ProductQuestionAnswer answer = new ProductQuestionAnswer(question, admin, request.getBody());
        answerRepository.save(answer);

        return ApiResponse.created(questionService.toResponse(question, true), "Answer added successfully");
    }

    // ── Update answer ─────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<QuestionResponse>> updateAnswer(
            UUID questionId, UpdateQuestionAnswerRequest request) {

        ProductQuestionAnswer answer = answerRepository
                .findByQuestionIdAndDeletedAtIsNull(questionId).orElse(null);
        if (answer == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Answer not found for this question");
        }

        answer.setBody(request.getBody());
        answerRepository.save(answer);

        ProductQuestion question = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElseThrow();
        return ApiResponse.success(questionService.toResponse(question, true), "Answer updated successfully");
    }

    // ── Toggle answer visibility ──────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<QuestionResponse>> toggleAnswer(UUID questionId) {
        ProductQuestionAnswer answer = answerRepository
                .findByQuestionIdAndDeletedAtIsNull(questionId).orElse(null);
        if (answer == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Answer not found for this question");
        }

        answer.setIsActive(!Boolean.TRUE.equals(answer.getIsActive()));
        answerRepository.save(answer);

        ProductQuestion question = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElseThrow();
        return ApiResponse.success(questionService.toResponse(question, true),
                "Answer visibility toggled to " + answer.getIsActive());
    }

    // ── Soft-delete answer ────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteAnswer(UUID questionId) {
        ProductQuestionAnswer answer = answerRepository
                .findByQuestionIdAndDeletedAtIsNull(questionId).orElse(null);
        if (answer == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Answer not found for this question");
        }
        answer.setDeletedAt(Instant.now());
        answerRepository.save(answer);
        return ApiResponse.success(null, "Answer deleted successfully");
    }
}
