package gov.openpto.odp.mapper;

import gov.openpto.odp.dto.PatentDetail;
import gov.openpto.odp.dto.PatentSummary;
import gov.openpto.odp.model.Party;
import gov.openpto.odp.model.Patent;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PatentMapper {

    int SNIPPET_LENGTH = 240;

    @Mapping(target = "abstractSnippet", expression = "java(snippet(patent.getAbstractText()))")
    PatentSummary toSummary(Patent patent);

    @Mapping(target = "abstractSnippet", expression = "java(snippet(patent.getAbstractText()))")
    PatentDetail toDetail(Patent patent);

    default List<String> names(List<Party> parties) {
        return parties == null ? List.of() : parties.stream().map(Party::getName).toList();
    }

    /**
     * First ~240 characters, cut at a word boundary, with an ellipsis when truncated. {@code @Named}
     * so MapStruct does not apply it implicitly to every String property.
     */
    @Named("snippet")
    default String snippet(String text) {
        if (text == null || text.length() <= SNIPPET_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf(' ', SNIPPET_LENGTH);
        return text.substring(0, cut > SNIPPET_LENGTH / 2 ? cut : SNIPPET_LENGTH).stripTrailing() + "…";
    }
}
