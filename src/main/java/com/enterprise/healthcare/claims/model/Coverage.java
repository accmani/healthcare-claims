package com.enterprise.healthcare.claims.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a member's insurance coverage plan.
 * Contains plan-level details including deductible, out-of-pocket maximum,
 * copay amounts, and the effective period of coverage.
 */
@Entity
@Table(name = "coverage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The type of coverage plan (HMO, PPO, EPO, HDHP).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_type", nullable = false)
    private CoverageType coverageType;

    /**
     * The annual deductible amount the member must pay before coverage begins.
     */
    @Column(name = "deductible_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal deductibleAmount;

    /**
     * The maximum out-of-pocket amount the member pays per plan year.
     */
    @Column(name = "out_of_pocket_max", precision = 10, scale = 2, nullable = false)
    private BigDecimal outOfPocketMax;

    /**
     * The fixed copay amount for covered services.
     */
    @Column(name = "copay_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal copayAmount;

    /**
     * The date this coverage becomes effective.
     */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /**
     * The date this coverage terminates. Null if coverage is open-ended.
     */
    @Column(name = "termination_date")
    private LocalDate terminationDate;

    /**
     * Indicates whether this coverage record is currently active.
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Enum representing the type of insurance coverage plan.
     */
    public enum CoverageType {
        /** Health Maintenance Organization - requires referrals, in-network only */
        HMO,
        /** Preferred Provider Organization - more flexibility, in/out-of-network */
        PPO,
        /** Exclusive Provider Organization - in-network only, no referrals needed */
        EPO,
        /** High Deductible Health Plan - higher deductible, lower premiums */
        HDHP
    }
}
