package gov.openpto.ingest.transform;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared Jackson 3 mapper for transform output (ISO dates, camelCase). */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    private Json() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }
}