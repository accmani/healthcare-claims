package com.hcsc.claims;

import java.math.BigDecimal;

public class ClaimAdjudicationService {

    public BigDecimal calculatePatientResponsibility(
            Claim claim, BigDecimal deductibleRemaining) {

        // FIX: Use >= 0 to correctly handle boundary case
        if (deductibleRemaining.compareTo(BigDecimal.ZERO) >= 0) {
            return claim.getCopayAmount();
        }
        return claim.getTotalAmount();
    }
}