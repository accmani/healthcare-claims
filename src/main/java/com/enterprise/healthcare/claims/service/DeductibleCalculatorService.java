package com.enterprise.healthcare.claims.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service responsible for calculating patient financial responsibility
 * including deductible application, coinsurance, and out-of-pocket maximum enforcement.
 *
 * Healthcare deductible logic:
 * - The deductible is the amount the member must pay before the plan begins covering costs.
 * - Once the deductible is met (year-to-date paid >= deductible amount), the plan pays
 *   its share and the member only pays copay/coinsurance.
 * - The out-of-pocket maximum caps total member spending per plan year.
 */
@Service
@Slf4j
public class DeductibleCalculatorService {

    private static final BigDecimal COINSURANCE_RATE = new BigDecimal("0.20"); // 20% coinsurance after deductible
    private static final int SCALE = 2;

    /**
     * Calculates the patient's financial responsibility for a given claim amount,
     * taking into account how much of the deductible has already been satisfied.
     *
     * @param claimAmount      the total allowed amount for the claim
     * @param yearToDatePaid   the amount the patient has already paid toward the deductible this plan year
     * @param deductibleAmount the patient's annual deductible
     * @return the amount the patient owes for this claim
     */
    public BigDecimal calculatePatientResponsibility(
            BigDecimal claimAmount,
            BigDecimal yearToDatePaid,
            BigDecimal deductibleAmount) {

        if (claimAmount == null || claimAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        log.debug("Calculating patient responsibility: claimAmount={}, yearToDatePaid={}, deductibleAmount={}",
                claimAmount, yearToDatePaid, deductibleAmount);

        // Fixed Bug #2: Changed > 0 to >= 0
        // When yearToDatePaid == deductibleAmount, deductible is fully met.
        if (yearToDatePaid.compareTo(deductibleAmount) >= 0) {
            // Deductible fully satisfied - patient only owes coinsurance
            BigDecimal coinsurance = calculateCoinsurance(claimAmount);
            log.debug("Deductible met. Patient owes coinsurance only: {}", coinsurance);
            return coinsurance;
        }

        // Calculate remaining deductible balance
        BigDecimal remainingDeductible = deductibleAmount.subtract(yearToDatePaid);

        if (claimAmount.compareTo(remainingDeductible) <= 0) {
            // Entire claim applies to deductible
            log.debug("Entire claim applies to deductible. Patient owes full claim amount: {}", claimAmount);
            return claimAmount;
        }

        // Part of claim applies to deductible, remainder subject to coinsurance
        BigDecimal amountAfterDeductible = claimAmount.subtract(remainingDeductible);
        BigDecimal coinsuranceOnRemainder = calculateCoinsurance(amountAfterDeductible);
        BigDecimal totalResponsibility = remainingDeductible.add(coinsuranceOnRemainder);

        log.debug("Partial deductible applies. Remaining deductible={}, coinsurance on remainder={}, total={}",
                remainingDeductible, coinsuranceOnRemainder, totalResponsibility);

        return totalResponsibility.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the coinsurance amount owed by the patient after the deductible is met.
     * Standard coinsurance is 20% of the allowed amount (plan pays 80%).
     *
     * @param allowedAmount the allowed amount subject to coinsurance
     * @return the patient's coinsurance obligation
     */
    public BigDecimal calculateCoinsurance(BigDecimal allowedAmount) {
        if (allowedAmount == null || allowedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return allowedAmount.multiply(COINSURANCE_RATE).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Enforces the out-of-pocket maximum by capping the patient's responsibility.
     * Once a member's cumulative payments reach the out-of-pocket maximum,
     * the plan covers 100% of covered services for the remainder of the plan year.
     *
     * @param proposedPatientAmount  the calculated patient responsibility before OOP max
     * @param yearToDateOutOfPocket  total amount patient has paid out-of-pocket this plan year
     * @param outOfPocketMax         the member's annual out-of-pocket maximum
     * @return the actual patient responsibility after applying OOP max
     */
    public BigDecimal calculateOutOfPocketMax(
            BigDecimal proposedPatientAmount,
            BigDecimal yearToDateOutOfPocket,
            BigDecimal outOfPocketMax) {

        if (proposedPatientAmount == null || proposedPatientAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal remainingOopCapacity = outOfPocketMax.subtract(yearToDateOutOfPocket);

        if (remainingOopCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            // Member has already reached OOP max - plan pays 100%
            log.debug("OOP max already reached. Patient owes $0 for this claim.");
            return BigDecimal.ZERO;
        }

        if (proposedPatientAmount.compareTo(remainingOopCapacity) > 0) {
            // Cap patient responsibility at remaining OOP capacity
            log.debug("OOP max cap applied. Proposed={}, Remaining OOP capacity={}, Capped at={}",
                    proposedPatientAmount, remainingOopCapacity, remainingOopCapacity);
            return remainingOopCapacity.setScale(SCALE, RoundingMode.HALF_UP);
        }

        return proposedPatientAmount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}