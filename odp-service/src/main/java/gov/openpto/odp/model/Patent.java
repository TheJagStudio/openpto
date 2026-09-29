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
import jakarta.persistence.OrderBy;
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

/**
 * A patent grant or pre-grant publication.
 *
 * <p>Rows are written through {@code PatentWriteRepository} (JDBC batches, used by the seed and
 * the internal bulk upsert); JPA is used for reads. Child collections are LAZY with batch
 * fetching so a search page loads each collection in a single extra query.
 */
@Entity
@Table(name = "patents")
@Getter
@Setter
public class Patent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patent_number", nullable = false, length = 32)
    private String patentNumber;

    @Column(name = "application_number", length = 32)
    private String applicationNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "abstract_text", columnDefinition = "text")
    private String abstractText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PatentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PatentStatus status;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(name = "grant_date")
    private LocalDate grantDate;

    @Column(name = "priority_date")
    private LocalDate priorityDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "primary_cpc", length = 32)
    private String primaryCpc;

    @Column(name = "cpc_section", length = 1)
    private String cpcSection;

    @Column(length = 200)
    private String examiner;

    @Column(name = "art_unit", length = 16)
    private String artUnit;

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

    /** Generated column; mapped only so Criteria queries can reference it in FTS functions. */
    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    @ElementCollection
    @CollectionTable(name = "patent_claims", joinColumns = @JoinColumn(name = "patent_id"))
    @OrderBy("number")
    @BatchSize(size = 100)
    private List<PatentClaim> claims = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "patent_inventors", joinColumns = @JoinColumn(name = "patent_id"))
    @OrderColumn(name = "seq")
    @BatchSize(size = 100)
    private List<Party> inventors = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "patent_assignees", joinColumns = @JoinColumn(name = "patent_id"))
    @OrderColumn(name = "seq")
    @BatchSize(size = 100)
    private List<Party> assignees = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "patent_cpc", joinColumns = @JoinColumn(name = "patent_id"))
    @OrderColumn(name = "seq")
    @Column(name = "code", nullable = false, length = 32)
    @BatchSize(size = 100)
    private List<String> cpcCodes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "patent_citations", joinColumns = @JoinColumn(name = "patent_id"))
    @OrderColumn(name = "seq")
    @BatchSize(size = 100)
    private List<PatentCitation> citations = new ArrayList<>();
}
