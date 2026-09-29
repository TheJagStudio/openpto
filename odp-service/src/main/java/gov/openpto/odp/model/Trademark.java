package gov.openpto.odp.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** A trademark application/registration (TSDR-style). Written via JDBC, read via JPA. */
@Entity
@Table(name = "trademarks")
@Getter
@Setter
public class Trademark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, length = 16)
    private String serialNumber;

    @Column(name = "registration_number", length = 16)
    private String registrationNumber;

    @Column(name = "mark_text", nullable = false, length = 500)
    private String markText;

    @Enumerated(EnumType.STRING)
    @Column(name = "mark_type", nullable = false, length = 24)
    private MarkType markType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TrademarkStatus status;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "status_date")
    private LocalDate statusDate;

    @Column(name = "owner_name", length = 300)
    private String owner;

    @Column(name = "owner_address", length = 500)
    private String ownerAddress;

    @Column(length = 200)
    private String attorney;

    @Column(name = "filing_basis", length = 4)
    private String filingBasis;

    /** Denormalized from goods & services so list pages and the class filter need no join. */
    @Column(name = "nice_classes", nullable = false, columnDefinition = "integer[]")
    private Integer[] niceClasses = new Integer[0];

    @Column(name = "goods_text", columnDefinition = "text")
    private String goodsText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private RecordSource source;

    @Column(name = "ingest_job_id", length = 64)
    private String ingestJobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    @ElementCollection
    @CollectionTable(name = "trademark_goods_services", joinColumns = @JoinColumn(name = "trademark_id"))
    @OrderColumn(name = "seq")
    @BatchSize(size = 100)
    private List<GoodsService> goodsAndServices = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "trademark_events", joinColumns = @JoinColumn(name = "trademark_id"))
    @OrderColumn(name = "seq")
    @BatchSize(size = 100)
    private List<TrademarkEvent> events = new ArrayList<>();
}
