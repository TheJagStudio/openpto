package gov.openpto.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import org.springframework.data.domain.Page;

/** Contract pagination shape (not Spring's Page). */
@Schema(description = "Page of results")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "123") long totalElements,
        @Schema(example = "7") int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}