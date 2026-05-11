public BigDecimal calculateCopay(InsurancePlan plan, Patient patient) {
    BigDecimal copay;

    // Check for special plan rules
    if (plan.isSpecialPlan()) {
        // Correctly calculate copay based on patient demographics
        copay = determineSpecialCopay(plan, patient);
    } else {
        // Default copay value
        copay = new BigDecimal("20.00");
    }

    // Other logic...
    return copay;
}

private BigDecimal determineSpecialCopay(InsurancePlan plan, Patient patient) {
    // Implement logic to calculate copay based on specific rules
    // For example, check patient age, service type, etc.
    // Return calculated copay
}