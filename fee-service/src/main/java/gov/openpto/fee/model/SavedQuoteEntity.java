package gov.openpto.fee.model;

import gov.openpto.fee.dto.QuoteKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

/**
 * A saved quote. Only the normalized request is authoritative; line items are recomputed on read.
 * {@code scheduleCode} and {@code total} are denormalized for listing/auditing.
 */
@Entity
@Table(name = "saved_quote")
@Getter
@Setter
public class SavedQuoteEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteKind kind;

    @Column(length = 200)
    private String label;

    /** Normalized request JSON (jsonb); written with an explicit cast so the driver can bind a String. */
    @Column(name = "request", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String requestJson;

    @Column(name = "schedule_code", nullable = false, length = 20)
    private String scheduleCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
