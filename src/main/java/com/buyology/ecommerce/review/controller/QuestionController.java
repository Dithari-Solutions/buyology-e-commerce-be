package com.buyology.ecommerce.review.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/questions")
@Tag(name = "Questions (Public)", description = "Public APIs for browsing and submitting product Q&A")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "Get approved questions for a product",
            description = "Ordered by helpful_count DESC, then created_at DESC.")
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getApprovedQuestionsByProduct(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return questionService.getApprovedQuestionsByProduct(productId, page, size);
    }

    @Operation(summary = "Get a single approved question by ID")
    @GetMapping("/{questionId}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getApprovedQuestion(
            @PathVariable UUID questionId) {
        return questionService.getApprovedQuestion(questionId);
    }

    @Operation(summary = "Ask a question about a product",
            description = "The question starts in PENDING status awaiting moderation.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(
            @RequestBody @Valid CreateQuestionRequest request) {
        return questionService.createQuestion(request);
    }

    @Operation(summary = "Delete (soft-delete) a question")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable UUID questionId) {
        return questionService.deleteQuestion(questionId);
    }

    @Operation(summary = "Mark a question as helpful")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{questionId}/vote")
    public ResponseEntity<ApiResponse<Void>> voteOnQuestion(
            @PathVariable UUID questionId,
            @RequestBody @Valid VoteQuestionRequest request) {
        return questionService.voteOnQuestion(questionId, request);
    }

    @Operation(summary = "Remove helpful vote from a question")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{questionId}/vote")
    public ResponseEntity<ApiResponse<Void>> removeVoteOnQuestion(
            @PathVariable UUID questionId,
            @RequestParam UUID authCredentialId) {
        return questionService.removeVoteOnQuestion(questionId, authCredentialId);
    }
}
