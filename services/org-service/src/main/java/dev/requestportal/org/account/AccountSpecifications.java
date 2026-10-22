package dev.requestportal.org.account;

import dev.requestportal.security.Role;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

final class AccountSpecifications {

    private AccountSpecifications() {
    }

    static Specification<Account> matching(String text, Role role, Long departmentId) {
        List<Specification<Account>> filters = new ArrayList<>();
        if (StringUtils.hasText(text)) {
            String pattern = "%" + escapeLike(normalizeForSearch(text)) + "%";
            filters.add((root, query, cb) -> cb.like(root.get("searchText"), pattern, '\\'));
        }
        if (role != null) {
            filters.add((root, query, cb) -> cb.isMember(role, root.<Set<Role>>get("roles")));
        }
        if (departmentId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId));
        }
        return Specification.allOf(filters);
    }

    /** Same folding as the database's search_text column: lower case, no Vietnamese diacritics. */
    static String normalizeForSearch(String text) {
        String withoutMarks = Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
