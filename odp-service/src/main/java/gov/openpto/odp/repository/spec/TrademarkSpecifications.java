package gov.openpto.odp.repository.spec;

import static gov.openpto.odp.repository.spec.SpecSupport.ESCAPE;
import static gov.openpto.odp.repository.spec.SpecSupport.hasText;

import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.model.Trademark;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

/** Criteria for the trademark search filters and ordering. */
public final class TrademarkSpecifications {

    public static final Set<String> SORT_FIELDS = Set.of("filingDate", "registrationDate", "serialNumber", SearchSort.RELEVANCE);
    public static final String DEFAULT_SORT = "filingDate";

    private TrademarkSpecifications() {
    }

    public static SearchSort sort(String raw, TrademarkFilter filter) {
        return SearchSort.parse(raw, SORT_FIELDS, filter.hasQuery(), DEFAULT_SORT);
    }

    public static Specification<Trademark> matching(TrademarkFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.hasQuery()) {
                predicates.add(cb.isTrue(cb.function("fts_match", Boolean.class, root.get("searchVector"), cb.literal(f.q().trim()))));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.niceClass() != null) {
                predicates.add(cb.isTrue(cb.function(
                        "int_array_contains", Boolean.class, root.get("niceClasses"), cb.literal(f.niceClass()))));
            }
            if (hasText(f.owner())) {
                predicates.add(cb.like(cb.lower(root.get("owner")), SpecSupport.containsPattern(f.owner()), ESCAPE));
            }
            if (f.filedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("filingDate"), f.filedFrom()));
            }
            if (f.filedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("filingDate"), f.filedTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Trademark> matchingOrdered(TrademarkFilter f, SearchSort sort) {
        Specification<Trademark> filters = matching(f);
        return (root, query, cb) -> {
            Predicate predicate = filters.toPredicate(root, query, cb);
            if (!SpecSupport.isCountQuery(query)) {
                query.orderBy(orders(root, cb, f, sort));
            }
            return predicate;
        };
    }

    static List<Order> orders(Root<Trademark> root, CriteriaBuilder cb, TrademarkFilter f, SearchSort sort) {
        List<Order> orders = new ArrayList<>();
        if (sort.isRelevance() && f.hasQuery()) {
            orders.add(cb.desc(cb.function("fts_rank", Double.class, root.get("searchVector"), cb.literal(f.q().trim()))));
        } else {
            Expression<?> expr = root.get(sort.isRelevance() ? DEFAULT_SORT : sort.field());
            orders.add(sort.ascending() ? cb.asc(expr, Nulls.LAST) : cb.desc(expr, Nulls.LAST));
        }
        orders.add(cb.desc(root.get("id")));
        return orders;
    }
}
