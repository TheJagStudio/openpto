package gov.openpto.fee.model;

import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.FeeUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fee_item")
@Getter
@Setter
public class FeeItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private FeeScheduleEntity schedule;

    @Column(name = "fee_code", nullable = false, length = 40)
    private String feeCode;

    @Column(nullable = false, length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeeCategory category;

    @Column(name = "fee_group", nullable = false, length = 60)
    private String group;

    @Column(name = "large_entity", nullable = false, precision = 12, scale = 2)
    private BigDecimal largeEntity;

    @Column(name = "small_entity", nullable = false, precision = 12, scale = 2)
    private BigDecimal smallEntity;

    @Column(name = "micro_entity", nullable = false, precision = 12, scale = 2)
    private BigDecimal microEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeeUnit unit;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
