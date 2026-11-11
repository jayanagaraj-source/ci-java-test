package com.example.taskapi.tasks;

import com.example.taskapi.auth.CurrentUser;
import com.example.taskapi.tasks.dto.TaskFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Builds the WHERE clause for task listing. Ownership is always applied for non-admins. */
final class TaskSpecifications {

    private static final char ESCAPE = '\\';

    private TaskSpecifications() {}

    static Specification<Task> matching(CurrentUser user, TaskFilter filter) {
        List<Specification<Task>> specs = new ArrayList<>();
        if (!user.isAdmin()) {
            specs.add((root, query, cb) -> cb.equal(root.get("ownerId"), user.id()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.priority() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("priority"), filter.priority()));
        }
        if (filter.tag() != null && !filter.tag().isBlank()) {
            String tag = filter.tag().trim().toLowerCase(Locale.ROOT);
            specs.add((root, query, cb) -> cb.isMember(tag, root.<java.util.Set<String>>get("tags")));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String pattern = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("description")), pattern, ESCAPE)));
        }
        return Specification.allOf(specs);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
