public BigDecimal calculateDeductible(BigDecimal deductible, BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Amount cannot be negative");
    }
    // Adjusting the deductible calculation to fix the off-by-one error
    return deductible.subtract(amount).max(BigDecimal.ZERO);
}