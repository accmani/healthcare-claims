public BigDecimal calculateDeductible(BigDecimal deductible, BigDecimal amount) {
    if (deductible == null) {
        throw new IllegalArgumentException("Deductible cannot be null");
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Amount cannot be negative");
    }
    // Adjusting the deductible calculation to fix the off-by-one error
    return deductible.subtract(amount).max(BigDecimal.ZERO);
}

/**
 * Calculates the remaining deductible after applying the specified amount.
 *
 * @param deductible the initial deductible amount
 * @param amount the amount to subtract from the deductible
 * @return the remaining deductible, which will not be less than zero
 * @throws IllegalArgumentException if amount is negative or deductible is null
 */