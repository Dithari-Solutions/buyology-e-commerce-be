package com.buyology.ecommerce.review.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.review.domain.*;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.repository.*;
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
public class QuestionService {

    private final ProductQuestionRepository questionRepository;
    private final ProductQuestionAnswerRepository answerRepository;
    private final ProductQuestionVoteRepository voteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public QuestionService(ProductQuestionRepository questionRepository,
                           ProductQuestionAnswerRepository answerRepository,
                           ProductQuestionVoteRepository voteRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.voteRepository = voteRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // ── Public: List approved questions for a product ─────────────────────────

    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getApprovedQuestionsByProduct(
            UUID productId, int page, int size) {

        Page<ProductQuestion> questions = questionRepository.findByProductIdAndStatusAndDeletedAtIsNull(
                productId, ModerationStatus.APPROVED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "helpfulCount", "createdAt")));

        List<QuestionResponse> data = questions.getContent().stream()
                .map(q -> toResponse(q, true))
                .toList();
        return ApiResponse.success(data, "Questions fetched successfully");
    }

    // ── Public: Get single approved question ─────────────────────────────────

    public ResponseEntity<ApiResponse<QuestionResponse>> getApprovedQuestion(UUID questionId) {
        ProductQuestion question = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElse(null);
        if (question == null || question.getStatus() != ModerationStatus.APPROVED) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }
        return ApiResponse.success(toResponse(question, true), "Question fetched successfully");
    }

    // ── Ask a question ────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(CreateQuestionRequest request) {
        Product product = productRepository.findById(request.getProductId()).orElse(null);
        if (product == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        ProductQuestion question = new ProductQuestion(product, user, request.getBody());
        ProductQuestion saved = questionRepository.save(question);
        return ApiResponse.created(toResponse(saved, false), "Question submitted successfully and is pending moderation");
    }

    // ── Soft-delete own question ──────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(UUID questionId) {
        ProductQuestion question = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElse(null);
        if (question == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }
        question.setDeletedAt(Instant.now());
        questionRepository.save(question);
        return ApiResponse.success(null, "Question deleted successfully");
    }

    // ── Vote a question as helpful ────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> voteOnQuestion(UUID questionId, VoteQuestionRequest request) {
        ProductQuestion question = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElse(null);
        if (question == null || question.getStatus() != ModerationStatus.APPROVED) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Question not found");
        }

        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        if (voteRepository.existsByIdQuestionIdAndIdUserId(questionId, request.getUserId())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "You have already voted on this question");
        }

        ProductQuestionVoteId voteId = new ProductQuestionVoteId(questionId, request.getUserId());
        ProductQuestionVote vote = new ProductQuestionVote(voteId, question, user);
        voteRepository.save(vote);

        question.setHelpfulCount(question.getHelpfulCount() + 1);
        questionRepository.save(question);
        return ApiResponse.success(null, "Vote recorded successfully");
    }

    // ── Remove helpful vote ───────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeVoteOnQuestion(UUID questionId, UUID userId) {
        ProductQuestionVote vote = voteRepository
                .findByIdQuestionIdAndIdUserId(questionId, userId).orElse(null);
        if (vote == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Vote not found");
        }

        ProductQuestion question = vote.getQuestion();
        question.setHelpfulCount(Math.max(0, question.getHelpfulCount() - 1));
        questionRepository.save(question);
        voteRepository.delete(vote);
        return ApiResponse.success(null, "Vote removed successfully");
    }

    // ── Mapping helper ────────────────────────────────────────────────────────

    public QuestionResponse toResponse(ProductQuestion question, boolean includeAnswer) {
        QuestionResponse r = new QuestionResponse();
        r.setId(question.getId());
        r.setProductId(question.getProduct().getId());
        r.setUserId(question.getUser().getId());
        r.setUserFirstName(question.getUser().getFirstName());
        r.setUserLastName(question.getUser().getLastName());
        r.setBody(question.getBody());
        r.setStatus(question.getStatus());
        r.setHelpfulCount(question.getHelpfulCount());
        r.setCreatedAt(question.getCreatedAt());
        r.setUpdatedAt(question.getUpdatedAt());

        if (includeAnswer) {
            answerRepository.findByQuestionIdAndDeletedAtIsNull(question.getId()).ifPresent(answer -> {
                if (Boolean.TRUE.equals(answer.getIsActive())) {
                    QuestionResponse.QuestionAnswerDto ad = new QuestionResponse.QuestionAnswerDto();
                    ad.setId(answer.getId());
                    ad.setAdminId(answer.getAdmin().getId());
                    ad.setAdminFirstName(answer.getAdmin().getFirstName());
                    ad.setAdminLastName(answer.getAdmin().getLastName());
                    ad.setBody(answer.getBody());
                    ad.setIsActive(answer.getIsActive());
                    ad.setCreatedAt(answer.getCreatedAt());
                    ad.setUpdatedAt(answer.getUpdatedAt());
                    r.setAnswer(ad);
                }
            });
        }

        return r;
    }
}
