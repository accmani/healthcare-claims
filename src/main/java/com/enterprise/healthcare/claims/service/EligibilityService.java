package com.enterprise.healthcare.claims.service;

import com.enterprise.healthcare.claims.model.Coverage;
import com.enterprise.healthcare.claims.model.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Service responsible for verifying patient eligibility for insurance coverage.
 *
 * Eligibility checks are performed prior to claims adjudication to ensure:
 * 1. The patient has an active coverage record on file
 * 2. The date of service falls within the coverage effective period
 * 3. The coverage record is flagged as active
 *
 * This is a real-time eligibility verification engine that integrates with
 * the internal member enrollment system.
 */
@Service
@Slf4j
public class EligibilityService {

    /**
     * Checks whether a patient has active, valid coverage for the given service date.
     *
     * @param patient     the patient whose eligibility is being verified
     * @param serviceDate the date services were (or will be) rendered
     * @return true if the patient is eligible; false otherwise
     */
    public boolean checkEligibility(Patient patient, LocalDate serviceDate) {
        if (patient == null) {
            log.warn("Eligibility check failed: patient is null");
            return false;
        }

        Coverage coverage = patient.getCoverage();

        // Patient must have a coverage record on file
        if (coverage == null) {
            log.info("Eligibility check failed for member {}: no coverage record on file",
                    patient.getMemberId());
            return false;
        }

        // Coverage record must be marked as active
        if (!coverage.isActive()) {
            log.info("Eligibility check failed for member {}: coverage is inactive",
                    patient.getMemberId());
            return false;
        }

        // Service date must be on or after the coverage effective date
        if (serviceDate.isBefore(coverage.getEffectiveDate())) {
            log.info("Eligibility check failed for member {}: service date {} is before coverage effective date {}",
                    patient.getMemberId(), serviceDate, coverage.getEffectiveDate());
            return false;
        }

        // If coverage has a termination date, service date must be before or equal to it
        if (coverage.getTerminationDate() != null && serviceDate.isAfter(coverage.getTerminationDate())) {
            log.info("Eligibility check failed for member {}: service date {} is after coverage termination date {}",
                    patient.getMemberId(), serviceDate, coverage.getTerminationDate());
            return false;
        }

        log.info("Eligibility confirmed for member {} under {} plan, effective {}",
                patient.getMemberId(), coverage.getCoverageType(), coverage.getEffectiveDate());
        return true;
    }

    /**
     * Returns the coverage object for a patient if eligible, or null if not eligible.
     * Convenience method for services that need the coverage object after eligibility is confirmed.
     *
     * @param patient     the patient to check
     * @param serviceDate the date of service
     * @return the Coverage object if eligible, null otherwise
     */
    public Coverage getActiveCoverage(Patient patient, LocalDate serviceDate) {
        if (checkEligibility(patient, serviceDate)) {
            return patient.getCoverage();
        }
        return null;
    }
}
