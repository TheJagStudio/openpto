package gov.openpto.odp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatentCitation {

    @Column(name = "cited_patent_number", nullable = false, length = 32)
    private String patentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "cited_by", nullable = false, length = 16)
    private CitedBy citedBy;
}
