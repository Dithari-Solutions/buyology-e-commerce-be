package com.buyology.ecommerce.common.response;

import java.util.List;

import org.springframework.data.domain.Page;

/** Lightweight, stable pagination envelope (Spring's Page serializes verbosely/unstably). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
