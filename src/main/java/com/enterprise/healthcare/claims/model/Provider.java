package com.enterprise.healthcare.claims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a healthcare provider (physician, hospital, clinic, etc.)
 * who renders services to patients. Network status determines reimbursement rates.
 */
@Entity
@Table(name = "provider")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * National Provider Identifier - 10-digit unique identifier assigned by CMS.
     */
    @Column(name = "npi", nullable = false, unique = true, length = 10)
    private String npi;

    /**
     * Legal name of the provider or provider organization.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Medical specialty of the provider (e.g., Internal Medicine, Cardiology).
     */
    @Column(name = "specialty")
    private String specialty;

    /**
     * Network participation status. Determines whether negotiated rates apply.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "network_status", nullable = false)
    private NetworkStatus networkStatus;

    /**
     * Federal Tax Identification Number used for payment and 1099 reporting.
     */
    @Column(name = "tax_id", length = 9)
    private String taxId;

    /**
     * Enum representing the provider's network participation status.
     */
    public enum NetworkStatus {
        /** Provider has a contract with the health plan - negotiated rates apply */
        IN_NETWORK,
        /** Provider does not have a contract - billed charges or UCR rates apply */
        OUT_OF_NETWORK
    }
}
