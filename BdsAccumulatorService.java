public BigDecimal calculateDeductible(BigDecimal deductible, BigDecimal amount) {
    if (deductible == null) {
        throw new IllegalArgumentException("Deductible cannot be null");
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Amount cannot be negative");
    }
    // Adjusting the deductible calculation to fix the off-by-one error
    if (amount.compareTo(deductible) > 0) {
        return BigDecimal.ZERO;
    }
    return deductible.subtract(amount);
}

/**
 * Calculates the remaining deductible after applying the specified amount.
 *
 * @param deductible the initial deductible amount
 * @param amount the amount to subtract from the deductible
 * @return the remaining deductible, which will not be less than zero
 * @throws IllegalArgumentException if amount is negative or deductible is null
 */

// Unit tests for edge cases
@Test
public void testCalculateDeductible() {
    BdsAccumulatorService service = new BdsAccumulatorService();
    assertEquals(BigDecimal.ZERO, service.calculateDeductible(new BigDecimal("100"), new BigDecimal("150")));
    assertEquals(new BigDecimal("50"), service.calculateDeductible(new BigDecimal("100"), new BigDecimal("50")));
    assertEquals(new BigDecimal("100"), service.calculateDeductible(new BigDecimal("100"), BigDecimal.ZERO));
}