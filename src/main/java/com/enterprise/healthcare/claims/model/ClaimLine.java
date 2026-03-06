package com.enterprise.healthcare.claims.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single service line item within a healthcare claim.
 * Each claim line corresponds to a distinct procedure or service rendered.
 * Maps to individual line items on a CMS-1500 or UB-04 claim form.
 */
@Entity
@Table(name = "claim_line")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * CPT or HCPCS procedure code identifying the service rendered.
     * Example: 99213 (Office visit, established patient, low complexity)
     */
    @Column(name = "procedure_code", nullable = false, length = 10)
    private String procedureCode;

    /**
     * ICD-10-CM diagnosis code justifying medical necessity.
     * Example: J06.9 (Acute upper respiratory infection, unspecified)
     */
    @Column(name = "diagnosis_code", nullable = false, length = 10)
    private String diagnosisCode;

    /**
     * The date the service was rendered to the patient.
     */
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    /**
     * The provider's submitted charge for this service line.
     * Must be greater than zero for the line to be processed.
     */
    @Column(name = "billed_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal billedAmount;

    /**
     * The maximum amount the plan will recognize for this service,
     * based on fee schedules or UCR rates. Set during adjudication.
     */
    @Column(name = "allowed_amount", precision = 10, scale = 2)
    private BigDecimal allowedAmount;

    /**
     * Number of units of the procedure performed.
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;
}
