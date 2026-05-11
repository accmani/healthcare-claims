public class BdsAccumulatorServiceTest {
    private BdsAccumulatorService service;

    @Before
    public void setUp() {
        service = new BdsAccumulatorService();
    }

    @Test
    public void testCalculateDeductible() {
        assertEquals(BigDecimal.ZERO, service.calculateDeductible(new BigDecimal("100"), new BigDecimal("150")));
        assertEquals(new BigDecimal("50"), service.calculateDeductible(new BigDecimal("100"), new BigDecimal("50")));
        assertEquals(BigDecimal.ZERO, service.calculateDeductible(new BigDecimal("100"), new BigDecimal("100")));
        assertEquals(new BigDecimal("100"), service.calculateDeductible(new BigDecimal("100"), BigDecimal.ZERO));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDeductible_NullDeductible() {
        service.calculateDeductible(null, new BigDecimal("50"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDeductible_NegativeDeductible() {
        service.calculateDeductible(new BigDecimal("-100"), new BigDecimal("50"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDeductible_NegativeAmount() {
        service.calculateDeductible(new BigDecimal("100"), new BigDecimal("-50"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDeductible_ZeroBoth() {
        service.calculateDeductible(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

public BigDecimal calculateDeductible(BigDecimal deductible, BigDecimal amount) {
    if (deductible == null) {
        throw new IllegalArgumentException("Deductible cannot be null");
    }
    if (deductible.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Deductible cannot be negative");
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Amount cannot be negative");
    }
    // Adjusting the deductible calculation to fix the off-by-one error
    if (amount.compareTo(deductible) >= 0) {
        return BigDecimal.ZERO; // Indicate that the deductible has been fully utilized
    }
    return deductible.subtract(amount);
}

/**
 * Calculates the remaining deductible after applying the specified amount.
 *
 * @param deductible the initial deductible amount
 * @param amount the amount to subtract from the deductible
 * @return the remaining deductible, which will not be less than zero
 * @throws IllegalArgumentException if amount is negative or deductible is null or negative
 */