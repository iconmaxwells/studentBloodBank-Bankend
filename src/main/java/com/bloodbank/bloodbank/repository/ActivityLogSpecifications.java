package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.ActivityLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

public final class ActivityLogSpecifications {

    private ActivityLogSpecifications() {}

    public static Specification<ActivityLog> withFilters(
            String category, UUID staffId, LocalDate from, LocalDate to, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase()));
            }
            if (staffId != null) {
                predicates.add(cb.equal(root.get("staffId"), staffId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to.atTime(LocalTime.MAX)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("action"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("staffName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern)
                ));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
