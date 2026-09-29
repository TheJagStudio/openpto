package gov.openpto.odp.repository;

import gov.openpto.odp.dto.FacetCount;
import gov.openpto.odp.model.Party;
import gov.openpto.odp.model.Patent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.LocalDateField;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** GROUP BY aggregations that reuse the search {@link Specification}, so facets honor every filter. */
@Repository
@RequiredArgsConstructor
public class PatentFacetRepository {

    private final EntityManager em;

    public List<FacetCount> countByAttribute(Specification<Patent> spec, String attribute, int limit) {
        return facet(spec, (root, cb) -> root.get(attribute), limit, false);
    }

    public List<FacetCount> countByFilingYear(Specification<Patent> spec) {
        return facet(spec, (root, cb) -> cb.extract(LocalDateField.YEAR, root.get("filingDate")), 200, true);
    }

    public List<FacetCount> topAssignees(Specification<Patent> spec, int limit) {
        return facet(spec, (root, cb) -> {
            Join<Patent, Party> assignee = root.join("assignees");
            return assignee.get("name");
        }, limit, false);
    }

    private List<FacetCount> facet(
            Specification<Patent> spec,
            BiFunction<Root<Patent>, CriteriaBuilder, Expression<?>> keyFn,
            int limit,
            boolean orderByKey) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Patent> root = cq.from(Patent.class);
        Expression<?> key = keyFn.apply(root, cb);
        Expression<Long> count = cb.count(root);
        cq.multiselect(key, count);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(cb.and(predicate, cb.isNotNull(key)));
        } else {
            cq.where(cb.isNotNull(key));
        }
        cq.groupBy(key);
        cq.orderBy(orderByKey ? List.of(cb.asc(key)) : List.of(cb.desc(count), cb.asc(key)));
        return em.createQuery(cq).setMaxResults(limit).getResultList().stream()
                .map(t -> new FacetCount(Objects.toString(t.get(0)), t.get(1, Long.class)))
                .toList();
    }
}
