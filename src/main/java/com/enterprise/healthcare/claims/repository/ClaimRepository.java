package com.enterprise.healthcare.claims.repository;

import com.enterprise.healthcare.claims.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Claim entities.
 * Provides standard CRUD operations plus domain-specific query methods
 * for claims processing and reporting workflows.
 */
@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /**
     * Finds a claim by its unique claim number.
     *
     * @param claimNumber the claim identifier (e.g., CLM-20240115-000001)
     * @return an Optional containing the claim if found
     */
    Optional<Claim> findByClaimNumber(String claimNumber);

    /**
     * Retrieves all claims associated with a specific patient member ID.
     *
     * @param memberId the patient's member identifier
     * @return list of claims for the patient, ordered by submission date desc
     */
    List<Claim> findByPatientMemberIdOrderBySubmissionDateDesc(String memberId);

    /**
     * Retrieves all claims with the specified status.
     *
     * @param status the claim status to filter by
     * @return list of claims with the given status
     */
    List<Claim> findByStatus(Claim.ClaimStatus status);

    /**
     * Retrieves claims submitted within a date range.
     *
     * @param startDate the beginning of the date range (inclusive)
     * @param endDate   the end of the date range (inclusive)
     * @return list of claims submitted within the range
     */
    List<Claim> findBySubmissionDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Retrieves all pending claims for a specific provider by NPI.
     *
     * @param npi    the provider's National Provider Identifier
     * @param status the claim status to filter by
     * @return list of matching claims
     */
    List<Claim> findByProviderNpiAndStatus(String npi, Claim.ClaimStatus status);

    /**
     * Counts the number of claims in each status for reporting dashboards.
     *
     * @param status the claim status to count
     * @return the number of claims with the given status
     */
    long countByStatus(Claim.ClaimStatus status);

    /**
     * Finds claims that exist for a given claim number (used for duplicate detection).
     *
     * @param claimNumber the claim number to check
     * @return true if a claim with this number already exists
     */
    boolean existsByClaimNumber(String claimNumber);

    /**
     * Retrieves claims submitted by a specific provider within a date range.
     * Used for provider audit and payment reconciliation.
     *
     * @param npi       the provider NPI
     * @param startDate start of the date range
     * @param endDate   end of the date range
     * @return list of matching claims
     */
    @Query("SELECT c FROM Claim c WHERE c.provider.npi = :npi " +
           "AND c.submissionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.submissionDate DESC")
    List<Claim> findClaimsByProviderAndDateRange(
            @Param("npi") String npi,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
