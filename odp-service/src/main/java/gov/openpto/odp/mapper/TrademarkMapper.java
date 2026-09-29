package gov.openpto.odp.mapper;

import gov.openpto.odp.dto.TrademarkDetail;
import gov.openpto.odp.dto.TrademarkSummary;
import gov.openpto.odp.model.Trademark;

import java.util.Arrays;
import java.util.List;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TrademarkMapper {

    TrademarkSummary toSummary(Trademark trademark);

    TrademarkDetail toDetail(Trademark trademark);

    default List<Integer> classes(Integer[] classes) {
        return classes == null ? List.of() : Arrays.asList(classes);
    }
}
