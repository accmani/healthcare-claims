package com.hcsc.claims;

public class ClaimAdjudicationService {

    public AdjudicationResult adjudicateClaim(Claim claim) {
        Coverage coverage = coverageRepository
            .findByMemberId(claim.getMemberId());

        // FIX: Explicit null check before calling isActive()
        if (coverage == null) {
            return AdjudicationResult.denied(
                "No coverage for member: " + claim.getMemberId());
        }

        if (!coverage.isActive()) {
            return AdjudicationResult.denied("Inactive coverage");
        }

        return processActiveClaim(claim, coverage);
    }
}