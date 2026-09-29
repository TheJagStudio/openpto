package gov.openpto.odp.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers PostgreSQL-specific functions for Criteria/HQL (loaded via {@code META-INF/services}).
 *
 * <ul>
 *   <li>{@code fts_match(vector, q)} &rarr; {@code vector @@ websearch_to_tsquery('english', q)} (GIN-indexed)</li>
 *   <li>{@code fts_rank(vector, q)} &rarr; {@code ts_rank_cd(vector, websearch_to_tsquery('english', q))}</li>
 *   <li>{@code int_array_contains(array, n)} &rarr; {@code array @> ARRAY[n]} (GIN-indexed)</li>
 * </ul>
 */
public class PostgresFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        var registry = contributions.getFunctionRegistry();
        var types = contributions.getTypeConfiguration().getBasicTypeRegistry();
        BasicType<Boolean> bool = types.resolve(StandardBasicTypes.BOOLEAN);
        BasicType<Double> dbl = types.resolve(StandardBasicTypes.DOUBLE);
        registry.registerPattern("fts_match", "(?1 @@ websearch_to_tsquery('english', ?2))", bool);
        registry.registerPattern("fts_rank", "ts_rank_cd(?1, websearch_to_tsquery('english', ?2))", dbl);
        registry.registerPattern("int_array_contains", "(?1 @> ARRAY[cast(?2 as integer)])", bool);
    }
}
