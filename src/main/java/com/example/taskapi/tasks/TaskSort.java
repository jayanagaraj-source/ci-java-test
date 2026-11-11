package com.example.taskapi.tasks;

import com.example.taskapi.common.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Parses {@code sort=-priority,dueDate}: comma-separated fields, '-' prefix for
 * descending. Only allow-listed fields are accepted; id is the final tie-breaker.
 */
final class TaskSort {

    private static final Map<String, String> FIELDS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "dueDate", "dueDate",
            "title", "title",
            "status", "status",
            "priority", "priorityRank");
    private static final int MAX_FIELDS = 4;

    private TaskSort() {}

    static Sort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        String[] tokens = raw.split(",");
        if (tokens.length > MAX_FIELDS) {
            throw ApiException.validation("sort", "at most " + MAX_FIELDS + " sort fields are allowed");
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String token : tokens) {
            String field = token.trim();
            boolean descending = field.startsWith("-");
            if (descending || field.startsWith("+")) {
                field = field.substring(1);
            }
            String property = FIELDS.get(field);
            if (property == null) {
                throw ApiException.validation("sort",
                        "unsupported sort field; allowed: " + String.join(", ", FIELDS.keySet().stream().sorted().toList()));
            }
            // Tasks without a dueDate sort last (hibernate.order_by.default_null_ordering).
            orders.add(new Sort.Order(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property));
        }
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }
}
