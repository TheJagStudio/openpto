package gov.openpto.odp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One entry of the TSDR-style prosecution history. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrademarkEvent {

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 500)
    private String description;
}
