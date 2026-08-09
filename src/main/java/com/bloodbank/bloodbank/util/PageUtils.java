package com.bloodbank.bloodbank.util;

import com.bloodbank.bloodbank.dto.common.PageMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageUtils {

    private PageUtils() {}

    public static PageRequest toPageRequest(int page, int limit, String sort) {
        int safePage = Math.max(page - 1, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Sort sortSpec = parseSort(sort);
        return PageRequest.of(safePage, safeLimit, sortSpec);
    }

    public static PageMeta toMeta(Page<?> page, int requestedPage, int requestedLimit) {
        return PageMeta.builder()
                .page(requestedPage)
                .limit(requestedLimit)
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        if (sort.startsWith("-")) {
            return Sort.by(Sort.Direction.DESC, sort.substring(1));
        }
        return Sort.by(Sort.Direction.ASC, sort);
    }
}
