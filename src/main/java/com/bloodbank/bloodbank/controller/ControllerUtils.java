package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.common.PageMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class ControllerUtils {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private ControllerUtils() {}

    static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    static UUID toUuid(String value) {
        return UUID.fromString(value);
    }

    @SuppressWarnings("unchecked")
    static <T> ApiResponse<List<T>> paged(Map<String, Object> result) {
        return ApiResponse.ok((List<T>) result.get("items"), (PageMeta) result.get("meta"));
    }
}
