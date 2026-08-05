// Updated validateClaimLines method with null checks
public void validateClaimLines(List<ClaimLine> claimLines) {
    if (claimLines == null) {
        throw new IllegalArgumentException("Claim lines cannot be null");
    }
    for (ClaimLine line : claimLines) {
        if (line == null) {
            throw new IllegalArgumentException("Claim line cannot be null");
        }
        // Additional validation logic
    }
}