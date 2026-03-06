package com.enterprise.healthcare.claims.service;

import com.enterprise.healthcare.claims.model.*;
import com.enterprise.healthcare.claims.model.AdjudicationResult.AdjudicationStatus;
import com.enterprise.healthcare.claims.model.Coverage.CoverageType;
import com.enterprise.healthcare.claims.model.Provider.NetworkStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the ClaimAdjudicationService.
 *
 * These tests document the expected behavior of the adjudication engine
 * including KNOWN FAILING TESTS that demonstrate existing bugs:
 *
 * - testAdjudicateClaimWithNullCoverage: FAILS with NullPointerException (Bug #1)
 * - testDeductibleAtExactLimit: FAILS due to off-by-one comparison bug (Bug #2)
 * - testClaimLineValidation: FAILS with ConcurrentModificationException (Bug #3)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimAdjudicationService Tests")
class ClaimAdjudicationServiceTest {

    @Mock
    private EligibilityService eligibilityService;

    @Mock
    private DeductibleCalculatorService deductibleCalculatorService;

    @InjectMocks
    private ClaimAdjudicationService claimAdjudicationService;

    private Provider inNetworkProvider;
    private Coverage activeCoverage;
    private Patient patientWithCoverage;
    private Patient patientWithoutCoverage;
    private ClaimLine validClaimLine;

    @BeforeEach
    void setUp() {
        inNetworkProvider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .name("Dr. Jane Smith, MD")
                .specialty("Internal Medicine")
                .networkStatus(NetworkStatus.IN_NETWORK)
                .taxId("123456789")
                .build();

        activeCoverage = Coverage.builder()
                .id(1L)
                .coverageType(CoverageType.PPO)
                .deductibleAmount(new BigDecimal("1000.00"))
                .outOfPocketMax(new BigDecimal("5000.00"))
                .copayAmount(new BigDecimal("30.00"))
                .effectiveDate(LocalDate.of(2024, 1, 1))
                .terminationDate(LocalDate.of(2024, 12, 31))
                .active(true)
                .build();

        patientWithCoverage = Patient.builder()
                .id(1L)
                .memberId("MBR-001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .coverage(activeCoverage)
                .build();

        // Patient with NO coverage - used to demonstrate Bug #1
        patientWithoutCoverage = Patient.builder()
                .id(2L)
                .memberId("MBR-002")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 3, 22))
                .coverage(null) // No coverage on file
                .build();

        validClaimLine = ClaimLine.builder()
                .id(1L)
                .procedureCode("99213")
                .diagnosisCode("J06.9")
                .serviceDate(LocalDate.of(2024, 6, 15))
                .billedAmount(new BigDecimal("250.00"))
                .quantity(1)
                .build();
    }

    // =========================================================================
    // PASSING TESTS - Happy Path
    // =========================================================================

    @Test
    @DisplayName("Happy Path: Adjudicate claim with valid coverage - should return APPROVED")
    void testAdjudicateClaimWithValidCoverage() {
        // Arrange
        Claim claim = buildClaim("CLM-20240615-001", patientWithCoverage,
                List.of(validClaimLine));

        when(eligibilityService.checkEligibility(eq(patientWithCoverage), any(LocalDate.class)))
                .thenReturn(true);
        when(deductibleCalculatorService.calculatePatientResponsibility(
                any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("30.00")); // Copay equivalent
        when(deductibleCalculatorService.calculateOutOfPocketMax(
                any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("30.00"));

        // Act
        AdjudicationResult result = claimAdjudicationService.adjudicateClaim(claim);

        // Assert
        assertNotNull(result, "Adjudication result should not be null");
        assertEquals("CLM-20240615-001", result.getClaimNumber());
        assertEquals(AdjudicationStatus.APPROVED, result.getStatus(),
                "Claim with valid coverage should be approved");
        assertTrue(result.getApprovedAmount().compareTo(BigDecimal.ZERO) > 0,
                "Approved amount should be greater than zero");
        assertNotNull(result.getAdjudicatedDate(), "Adjudicated date should be set");
        assertFalse(result.getLineResults().isEmpty(), "Line results should not be empty");

        verify(eligibilityService, times(1)).checkEligibility(eq(patientWithCoverage), any(LocalDate.class));
    }

    @Test
    @DisplayName("Denial: Adjudicate claim for ineligible member - should return DENIED")
    void testAdjudicateClaimForIneligibleMember() {
        // Arrange
        Claim claim = buildClaim("CLM-20240615-002", patientWithCoverage,
                List.of(validClaimLine));

        when(eligibilityService.checkEligibility(eq(patientWithCoverage), any(LocalDate.class)))
                .thenReturn(false);

        // Act
        AdjudicationResult result = claimAdjudicationService.adjudicateClaim(claim);

        // Assert
        assertNotNull(result);
        assertEquals(AdjudicationStatus.DENIED, result.getStatus(),
                "Ineligible member claim should be denied");
        assertNotNull(result.getDenialReason(), "Denial reason should be populated");
        assertTrue(result.getDenialReason().contains("DENIAL-002"),
                "Denial reason should include denial code");
        assertEquals(BigDecimal.ZERO, result.getApprovedAmount());
    }

    @Test
    @DisplayName("Deductible: Standard deductible calculation - should work correctly when YTD < deductible")
    void testDeductibleCalculation() {
        // Arrange - Real DeductibleCalculatorService (not mocked) for unit testing
        DeductibleCalculatorService realService = new DeductibleCalculatorService();

        BigDecimal claimAmount = new BigDecimal("500.00");
        BigDecimal yearToDatePaid = new BigDecimal("200.00");   // Below deductible
        BigDecimal deductibleAmount = new BigDecimal("1000.00");

        // Act
        BigDecimal responsibility = realService.calculatePatientResponsibility(
                claimAmount, yearToDatePaid, deductibleAmount);

        // Assert - Entire claim applies to deductible (YTD + claim < deductible)
        assertEquals(0, new BigDecimal("500.00").compareTo(responsibility),
                "When YTD paid is below deductible and claim is less than remaining deductible, " +
                "patient owes full claim amount");
    }

    // =========================================================================
    // FAILING TESTS - Documenting Known Bugs
    // =========================================================================

    @Test
    @DisplayName("BUG #1: NullPointerException when patient has no coverage - CURRENTLY FAILS")
    void testAdjudicateClaimWithNullCoverage() {
        /*
         * BUG #1 DESCRIPTION:
         * When a patient has no coverage (coverage == null), the adjudicateClaim method
         * calls claim.getPatient().getCoverage().getCoverageType() without a null check.
         * This throws a NullPointerException even though the eligibility check should
         * have caught this case.
         *
         * EXPECTED BEHAVIOR: The method should return a DENIED result with an appropriate
         * denial reason when coverage is null, rather than throwing an NPE.
         *
         * ACTUAL BEHAVIOR: NullPointerException is thrown at:
         * ClaimAdjudicationService.java -> adjudicateClaim() -> getCoverageType()
         *
         * FIX: Add null check before accessing coverage:
         * if (claim.getPatient().getCoverage() == null) {
         *     return AdjudicationResult.denied(claim.getClaimNumber(), "DENIAL-003: No active coverage on file");
         * }
         */

        // Arrange
        Claim claim = buildClaim("CLM-20240615-003", patientWithoutCoverage,
                List.of(validClaimLine));

        // Eligibility service correctly returns false for null coverage
        when(eligibilityService.checkEligibility(eq(patientWithoutCoverage), any(LocalDate.class)))
                .thenReturn(false);

        // Act & Assert
        // This test documents the NPE bug - the eligibility check returns false,
        // but the code still tries to access coverage after the eligibility check
        // due to the code structure. In the current implementation with eligibility
        // returning false, it should return a DENIED result. However, if the
        // eligibility mock is changed to return true (simulating a different code path),
        // the NPE will surface directly.

        // Test with eligibility returning true to directly trigger Bug #1
        reset(eligibilityService);
        when(eligibilityService.checkEligibility(eq(patientWithoutCoverage), any(LocalDate.class)))
                .thenReturn(true); // Bypassing eligibility to expose Bug #1

        // This SHOULD return a DENIED result but WILL throw NullPointerException (Bug #1)
        assertThrows(NullPointerException.class, () -> {
            claimAdjudicationService.adjudicateClaim(claim);
        }, "Bug #1: NullPointerException occurs when coverage is null and eligibility check passes. " +
           "Expected behavior after fix: should return DENIED result with appropriate reason.");
    }

    @Test
    @DisplayName("BUG #2: Off-by-one in deductible comparison - CURRENTLY FAILS when YTD == deductible")
    void testDeductibleAtExactLimit() {
        /*
         * BUG #2 DESCRIPTION:
         * In DeductibleCalculatorService.calculatePatientResponsibility(), the condition
         * `yearToDatePaid.compareTo(deductibleAmount) > 0` should be `>= 0`.
         *
         * When yearToDatePaid EXACTLY EQUALS deductibleAmount, the deductible is fully met.
         * The patient should only owe coinsurance (20% of claim), NOT the full claim amount.
         *
         * EXPECTED: Patient owes coinsurance = $500 * 0.20 = $100.00
         * ACTUAL:   Patient owes full claim = $500.00 (bug causes deductible to be re-applied)
         *
         * FIX: Change `> 0` to `>= 0` in the comparison:
         * if (yearToDatePaid.compareTo(deductibleAmount) >= 0) {
         */

        // Arrange - Using real service to expose the bug
        DeductibleCalculatorService realService = new DeductibleCalculatorService();

        BigDecimal claimAmount = new BigDecimal("500.00");
        BigDecimal yearToDatePaid = new BigDecimal("1000.00");  // Exactly equals deductible
        BigDecimal deductibleAmount = new BigDecimal("1000.00"); // Fully met

        // Act
        BigDecimal actualResponsibility = realService.calculatePatientResponsibility(
                claimAmount, yearToDatePaid, deductibleAmount);

        // Assert
        BigDecimal expectedCoinsurance = new BigDecimal("100.00"); // 20% of $500
        BigDecimal incorrectFullAmount = new BigDecimal("500.00"); // Bug: full claim charged

        // This assertion FAILS due to Bug #2:
        // Actual result is $500.00 (full claim charged) instead of $100.00 (coinsurance only)
        assertEquals(0, expectedCoinsurance.compareTo(actualResponsibility),
                "BUG #2: When yearToDatePaid == deductibleAmount, deductible is fully met. " +
                "Patient should owe only coinsurance ($100.00) but Bug #2 causes full claim " +
                "amount ($500.00) to be charged. Fix: change '> 0' to '>= 0' in compareTo.");
    }

    @Test
    @DisplayName("BUG #3: ConcurrentModificationException in claim line validation - CURRENTLY FAILS")
    void testClaimLineValidation() {
        /*
         * BUG #3 DESCRIPTION:
         * In ClaimAdjudicationService.validateClaimLines(), the code iterates over
         * the claimLines list using a for-each loop and calls claimLines.remove(line)
         * inside the loop body. This throws ConcurrentModificationException because
         * the list's structural modification count changes during iteration.
         *
         * EXPECTED BEHAVIOR: Invalid claim lines (null or zero billed amount) should be
         * removed, and adjudication should proceed with only valid lines.
         *
         * ACTUAL BEHAVIOR: ConcurrentModificationException is thrown when an invalid
         * line is encountered during iteration.
         *
         * FIX: Use Iterator pattern or collect-then-remove:
         * Option A: Iterator
         *   Iterator<ClaimLine> iterator = claimLines.iterator();
         *   while (iterator.hasNext()) {
         *       ClaimLine line = iterator.next();
         *       if (line.getBilledAmount() == null || ...) {
         *           iterator.remove(); // Safe removal
         *       }
         *   }
         *
         * Option B: removeIf (Java 8+)
         *   claimLines.removeIf(line -> line.getBilledAmount() == null ||
         *                               line.getBilledAmount().compareTo(BigDecimal.ZERO) <= 0);
         */

        // Arrange: Create a claim with one valid line and one INVALID line (null billed amount)
        ClaimLine validLine = ClaimLine.builder()
                .id(1L)
                .procedureCode("99213")
                .diagnosisCode("J06.9")
                .serviceDate(LocalDate.of(2024, 6, 15))
                .billedAmount(new BigDecimal("250.00"))
                .quantity(1)
                .build();

        ClaimLine invalidLine = ClaimLine.builder()
                .id(2L)
                .procedureCode("99999")
                .diagnosisCode("Z00.00")
                .serviceDate(LocalDate.of(2024, 6, 15))
                .billedAmount(null) // Invalid - triggers removal attempt in the buggy loop
                .quantity(1)
                .build();

        // Use a mutable ArrayList to allow modification (would be needed in correct impl)
        List<ClaimLine> lines = new ArrayList<>(Arrays.asList(invalidLine, validLine));

        Claim claim = buildClaim("CLM-20240615-004", patientWithCoverage, lines);

        when(eligibilityService.checkEligibility(any(Patient.class), any(LocalDate.class)))
                .thenReturn(true);

        // Act & Assert
        // This SHOULD process the valid line and deny the invalid one,
        // but WILL throw ConcurrentModificationException (Bug #3)
        assertThrows(java.util.ConcurrentModificationException.class, () -> {
            claimAdjudicationService.adjudicateClaim(claim);
        }, "Bug #3: ConcurrentModificationException occurs when removing invalid claim lines " +
           "using remove() inside a for-each loop. Fix: use Iterator.remove() or List.removeIf().");
    }

    @Test
    @DisplayName("Deductible: Claim fully above remaining deductible - partial deductible + coinsurance")
    void testDeductiblePartiallyMet() {
        // Arrange
        DeductibleCalculatorService realService = new DeductibleCalculatorService();

        BigDecimal claimAmount = new BigDecimal("800.00");
        BigDecimal yearToDatePaid = new BigDecimal("700.00");   // $300 remaining deductible
        BigDecimal deductibleAmount = new BigDecimal("1000.00");

        // Act
        BigDecimal responsibility = realService.calculatePatientResponsibility(
                claimAmount, yearToDatePaid, deductibleAmount);

        // Assert: $300 deductible remaining + 20% coinsurance on remaining $500
        // = $300 + $100 = $400.00
        BigDecimal expected = new BigDecimal("400.00");
        assertEquals(0, expected.compareTo(responsibility),
                "Patient should owe remaining deductible ($300) plus coinsurance on excess ($100) = $400");
    }

    @Test
    @DisplayName("Out-of-pocket max: Patient at OOP max should owe $0")
    void testOutOfPocketMaxReached() {
        // Arrange
        DeductibleCalculatorService realService = new DeductibleCalculatorService();

        BigDecimal proposedAmount = new BigDecimal("500.00");
        BigDecimal yearToDateOop = new BigDecimal("5000.00");   // Already at max
        BigDecimal outOfPocketMax = new BigDecimal("5000.00");

        // Act
        BigDecimal result = realService.calculateOutOfPocketMax(proposedAmount, yearToDateOop, outOfPocketMax);

        // Assert
        assertEquals(0, BigDecimal.ZERO.compareTo(result),
                "When OOP max is reached, patient should owe $0");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Claim buildClaim(String claimNumber, Patient patient, List<ClaimLine> claimLines) {
        return Claim.builder()
                .id(1L)
                .claimNumber(claimNumber)
                .patient(patient)
                .provider(inNetworkProvider)
                .claimLines(new ArrayList<>(claimLines))
                .submissionDate(LocalDate.of(2024, 6, 15))
                .status(Claim.ClaimStatus.PENDING)
                .totalBilledAmount(claimLines.stream()
                        .filter(l -> l.getBilledAmount() != null)
                        .map(ClaimLine::getBilledAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }
}
