package com.enterprise.healthcare.claims.controller;

import com.enterprise.healthcare.claims.model.AdjudicationResult;
import com.enterprise.healthcare.claims.model.Claim;
import com.enterprise.healthcare.claims.repository.ClaimRepository;
import com.enterprise.healthcare.claims.service.ClaimAdjudicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for healthcare claims management and adjudication.
 *
 * Exposes endpoints for:
 * - Claim submission (ANSI X12 837 equivalent via JSON)
 * - Claim status inquiry (ANSI X12 276/277 equivalent)
 * - On-demand adjudication processing
 * - Claim listing and filtering
 *
 * Base URL: /api/claims
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@Slf4j
public class ClaimController {

    private final ClaimAdjudicationService claimAdjudicationService;
    private final ClaimRepository claimRepository;

    /**
     * Submits a new healthcare claim for processing.
     *
     * Note: @Valid annotation is intentionally omitted here to allow
     * loose validation at the API boundary. Validation is performed
     * within the adjudication service during processing.
     *
     * POST /api/claims
     *
     * @param claim the claim to submit (request body, JSON)
     * @return the saved claim with its assigned ID and PENDING status
     */
    @PostMapping
    public ResponseEntity<?> submitClaim(@RequestBody Claim claim) {
        log.info("Received claim submission: claimNumber={}", claim.getClaimNumber());

        // Check for duplicate claim numbers
        if (claimRepository.existsByClaimNumber(claim.getClaimNumber())) {
            log.warn("Duplicate claim submission rejected: {}", claim.getClaimNumber());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Duplicate claim",
                            "message", "Claim number " + claim.getClaimNumber() + " already exists",
                            "claimNumber", claim.getClaimNumber()
                    ));
        }

        // Set initial status and submission date
        claim.setStatus(Claim.ClaimStatus.PENDING);
        if (claim.getSubmissionDate() == null) {
            claim.setSubmissionDate(LocalDate.now());
        }

        Claim savedClaim = claimRepository.save(claim);
        log.info("Claim {} saved with ID {}", savedClaim.getClaimNumber(), savedClaim.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(savedClaim);
    }

    /**
     * Retrieves the current status and details of a specific claim.
     *
     * GET /api/claims/{claimNumber}
     *
     * @param claimNumber the unique claim identifier
     * @return the claim details, or 404 if not found
     */
    @GetMapping("/{claimNumber}")
    public ResponseEntity<?> getClaimStatus(@PathVariable String claimNumber) {
        log.debug("Fetching claim status for: {}", claimNumber);

        return claimRepository.findByClaimNumber(claimNumber)
                .<ResponseEntity<?>>map(claim -> {
                    log.debug("Found claim {}: status={}", claimNumber, claim.getStatus());
                    return ResponseEntity.ok(claim);
                })
                .orElseGet(() -> {
                    log.warn("Claim not found: {}", claimNumber);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of(
                                    "error", "Claim not found",
                                    "claimNumber", claimNumber,
                                    "message", "No claim exists with claim number: " + claimNumber
                            ));
                });
    }

    /**
     * Lists all claims in the system, optionally filtered by status.
     *
     * GET /api/claims
     * GET /api/claims?status=PENDING
     *
     * @param status optional status filter (PENDING, APPROVED, DENIED, PARTIAL)
     * @return list of claims matching the filter criteria
     */
    @GetMapping
    public ResponseEntity<List<Claim>> listClaims(
            @RequestParam(required = false) Claim.ClaimStatus status) {

        List<Claim> claims;
        if (status != null) {
            log.debug("Listing claims with status: {}", status);
            claims = claimRepository.findByStatus(status);
        } else {
            log.debug("Listing all claims");
            claims = claimRepository.findAll();
        }

        log.debug("Returning {} claims", claims.size());
        return ResponseEntity.ok(claims);
    }

    /**
     * Triggers on-demand adjudication for a specific claim.
     * This endpoint is used to process PENDING claims through the adjudication engine.
     *
     * POST /api/claims/{claimNumber}/adjudicate
     *
     * @param claimNumber the claim number to adjudicate
     * @return the adjudication result with financial breakdown
     */
    @PostMapping("/{claimNumber}/adjudicate")
    public ResponseEntity<?> adjudicateClaim(@PathVariable String claimNumber) {
        log.info("Adjudication requested for claim: {}", claimNumber);

        return claimRepository.findByClaimNumber(claimNumber)
                .<ResponseEntity<?>>map(claim -> {
                    if (claim.getStatus() != Claim.ClaimStatus.PENDING) {
                        log.warn("Adjudication skipped for claim {}: status is {} (not PENDING)",
                                claimNumber, claim.getStatus());
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of(
                                        "error", "Claim already processed",
                                        "claimNumber", claimNumber,
                                        "currentStatus", claim.getStatus().name(),
                                        "message", "Only PENDING claims can be adjudicated"
                                ));
                    }

                    try {
                        AdjudicationResult result = claimAdjudicationService.adjudicateClaim(claim);

                        // Update claim status based on adjudication result
                        Claim.ClaimStatus newStatus = mapAdjudicationStatusToClaimStatus(result.getStatus());
                        claim.setStatus(newStatus);
                        claimRepository.save(claim);

                        log.info("Claim {} adjudicated: status={}, approvedAmount={}",
                                claimNumber, result.getStatus(), result.getApprovedAmount());

                        return ResponseEntity.ok(result);

                    } catch (Exception e) {
                        log.error("Adjudication failed for claim {}: {}", claimNumber, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                        "error", "Adjudication failed",
                                        "claimNumber", claimNumber,
                                        "message", e.getMessage()
                                ));
                    }
                })
                .orElseGet(() -> {
                    log.warn("Adjudication failed: claim not found: {}", claimNumber);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of(
                                    "error", "Claim not found",
                                    "claimNumber", claimNumber
                            ));
                });
    }

    /**
     * Maps adjudication status to the corresponding claim lifecycle status.
     */
    private Claim.ClaimStatus mapAdjudicationStatusToClaimStatus(AdjudicationResult.AdjudicationStatus adjStatus) {
        return switch (adjStatus) {
            case APPROVED -> Claim.ClaimStatus.APPROVED;
            case DENIED -> Claim.ClaimStatus.DENIED;
            case PARTIAL -> Claim.ClaimStatus.PARTIAL;
        };
    }
}
