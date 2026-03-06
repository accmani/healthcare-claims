package com.enterprise.healthcare.claims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents a patient/member in the healthcare claims system.
 * A patient may or may not have active coverage at any given time.
 * Coverage can be null if the patient is uninsured or coverage has not been set up.
 */
@Entity
@Table(name = "patient")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique member identifier assigned by the health plan.
     * This is distinct from the internal database ID.
     */
    @NotBlank(message = "Member ID is required")
    @Column(name = "member_id", nullable = false, unique = true)
    private String memberId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /**
     * Patient's date of birth - used for age-based adjudication rules.
     */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * The patient's current insurance coverage. This field can be null
     * if the patient does not have active coverage on file.
     * Callers must null-check this field before accessing coverage details.
     */
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "coverage_id", referencedColumnName = "id")
    private Coverage coverage;

    /**
     * Returns the patient's full name for display purposes.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
