package com.buyology.ecommerce.review.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.review.dto.*;
import com.buyology.ecommerce.review.service.QuestionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasRole('SUPERADMIN') or hasRole('CUSTOMER_SUPPORT') or hasRole('ADMIN')")
@Tag(name = "Questions (Admin)", description = "Admin APIs for moderating questions and managing answers")
public class QuestionAdminController {

    private final QuestionAdminService questionAdminService;

    public QuestionAdminController(QuestionAdminService questionAdminService) {
        this.questionAdminService = questionAdminService;
    }

    @Operation(summary = "List all questions",
            description = "Filter by status: PENDING, APPROVED, REJECTED. Omit status to get all non-deleted questions.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getAllQuestions(
            @RequestParam(required = false) ModerationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return questionAdminService.getAllQuestions(status, page, size);
    }

    @Operation(summary = "Get a question by ID (includes deleted records)")
    @GetMapping("/{questionId}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestionById(
            @PathVariable UUID questionId) {
        return questionAdminService.getQuestionById(questionId);
    }

    @Operation(summary = "Approve or reject a question")
    @PatchMapping("/{questionId}/moderate")
    public ResponseEntity<ApiResponse<QuestionResponse>> moderateQuestion(
            @PathVariable UUID questionId,
            @AuthenticationPrincipal UUID adminId,
            @RequestBody @Valid ModerateQuestionRequest request) {
        return questionAdminService.moderateQuestion(questionId, request, adminId);
    }

    @Operation(summary = "Delete a question (soft-delete)")
    @DeleteMapping("/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable UUID questionId) {
        return questionAdminService.deleteQuestion(questionId);
    }

    @Operation(summary = "Add an admin answer to a question",
            description = "Only one answer per question is allowed. Use PUT to update an existing answer.")
    @PostMapping("/{questionId}/answer")
    public ResponseEntity<ApiResponse<QuestionResponse>> addAnswer(
            @PathVariable UUID questionId,
            @AuthenticationPrincipal UUID adminId,
            @RequestBody @Valid CreateQuestionAnswerRequest request) {
        return questionAdminService.addAnswer(questionId, request, adminId);
    }

    @Operation(summary = "Update the admin answer for a question")
    @PutMapping("/{questionId}/answer")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateAnswer(
            @PathVariable UUID questionId,
            @RequestBody @Valid UpdateQuestionAnswerRequest request) {
        return questionAdminService.updateAnswer(questionId, request);
    }

    @Operation(summary = "Toggle the visibility of an answer (isActive toggle)")
    @PatchMapping("/{questionId}/answer/toggle")
    public ResponseEntity<ApiResponse<QuestionResponse>> toggleAnswer(
            @PathVariable UUID questionId) {
        return questionAdminService.toggleAnswer(questionId);
    }

    @Operation(summary = "Delete the admin answer for a question (soft-delete)")
    @DeleteMapping("/{questionId}/answer")
    public ResponseEntity<ApiResponse<Void>> deleteAnswer(
            @PathVariable UUID questionId) {
        return questionAdminService.deleteAnswer(questionId);
    }
}
