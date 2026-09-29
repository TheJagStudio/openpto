package gov.openpto.odp.repository.spec;

import static gov.openpto.odp.repository.spec.SpecSupport.ESCAPE;
import static gov.openpto.odp.repository.spec.SpecSupport.hasText;

import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.model.Party;
import gov.openpto.odp.model.Patent;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

/** Criteria for the patent search filters and ordering. */
public final class PatentSpecifications {

    public static final Set<String> SORT_FIELDS = Set.of("grantDate", "filingDate", "patentNumber", SearchSort.RELEVANCE);
    public static final String DEFAULT_SORT = "filingDate";

    private PatentSpecifications() {
    }

    public static SearchSort sort(String raw, PatentFilter filter) {
        return SearchSort.parse(raw, SORT_FIELDS, filter.hasQuery(), DEFAULT_SORT);
    }

    /** Filters only (used for search, facets and counts). */
    public static Specification<Patent> matching(PatentFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.hasQuery()) {
                predicates.add(cb.isTrue(cb.function("fts_match", Boolean.class, root.get("searchVector"), cb.literal(f.q().trim()))));
            }
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (hasText(f.cpc())) {
                predicates.add(cpcPrefix(root, query, cb, normalizeCpc(f.cpc())));
            }
            if (hasText(f.assignee())) {
                predicates.add(partyNameContains(root, query, cb, "assignees", f.assignee()));
            }
            if (hasText(f.inventor())) {
                predicates.add(partyNameContains(root, query, cb, "inventors", f.inventor()));
            }
            if (f.filedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("filingDate"), f.filedFrom()));
            }
            if (f.filedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("filingDate"), f.filedTo()));
            }
            if (f.grantedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("grantDate"), f.grantedFrom()));
            }
            if (f.grantedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("grantDate"), f.grantedTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Filters plus ORDER BY (skipped for the count query). */
    public static Specification<Patent> matchingOrdered(PatentFilter f, SearchSort sort) {
        Specification<Patent> filters = matching(f);
        return (root, query, cb) -> {
            Predicate predicate = filters.toPredicate(root, query, cb);
            if (!SpecSupport.isCountQuery(query)) {
                query.orderBy(orders(root, cb, f, sort));
            }
            return predicate;
        };
    }

    static List<Order> orders(Root<Patent> root, CriteriaBuilder cb, PatentFilter f, SearchSort sort) {
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

    public static String normalizeCpc(String cpc) {
        return cpc.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static Predicate cpcPrefix(Root<Patent> root, CriteriaQuery<?> query, CriteriaBuilder cb, String prefix) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<Patent> correlated = sq.correlate(root);
        Join<Patent, String> code = correlated.join("cpcCodes");
        sq.select(cb.literal(1)).where(cb.like(code, SpecSupport.prefixPattern(prefix), ESCAPE));
        return cb.exists(sq);
    }

    private static Predicate partyNameContains(
            Root<Patent> root, CriteriaQuery<?> query, CriteriaBuilder cb, String collection, String text) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<Patent> correlated = sq.correlate(root);
        Join<Patent, Party> party = correlated.join(collection);
        sq.select(cb.literal(1)).where(cb.like(cb.lower(party.get("name")), SpecSupport.containsPattern(text), ESCAPE));
        return cb.exists(sq);
    }
}
