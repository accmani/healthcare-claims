// Fix for NullPointerException in ClaimController.java
if (claim.getClaimNumber() != null && claimRepository.existsByClaimNumber(claim.getClaimNumber())) {
    // existing logic
}

if (claim != null && claim.getSubmissionDate() == null) {
    // existing logic
}