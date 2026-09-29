package gov.openpto.odp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An inventor or assignee of a patent. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Party {

    @Column(nullable = false, length = 300)
    private String name;

    @Column(length = 120)
    private String city;

    @Column(length = 60)
    private String state;

    @Column(length = 2)
    private String country;
}
