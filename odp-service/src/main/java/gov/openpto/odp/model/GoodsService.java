package gov.openpto.odp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoodsService {

    @Column(name = "nice_class", nullable = false)
    private Integer niceClass;

    @Column(nullable = false, columnDefinition = "text")
    private String description;
}
