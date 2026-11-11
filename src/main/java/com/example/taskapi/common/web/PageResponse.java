package com.example.taskapi.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Paginated response envelope. {@code page} is 1-based. */
public record PageResponse<T>(List<T> items, int page, int limit, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
