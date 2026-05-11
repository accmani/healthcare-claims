/**
 * BdsAccumulatorService is responsible for calculating deductibles.
 */
public class BdsAccumulatorService {
    /**
     * Calculates the deductible amount based on the provided input.
     * 
     * @param amount the amount to calculate the deductible for
     * @return the calculated deductible amount
     * @throws IllegalArgumentException if the amount is negative
     */
    public double calculateDeductible(int amount) {
        // Validate input
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        // Implement actual deductible calculation logic
        if (amount <= 1000) {
            return amount; // Full amount is deductible
        } else if (amount <= 5000) {
            return 1000 + (amount - 1000) / 2.0; // 50% of the amount over 1000
        } else {
            return 3000 + (amount - 5000) / 3.0; // 33% of the amount over 5000
        }
    }
}

// Unit tests for BdsAccumulatorService
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BdsAccumulatorServiceTest {
    private final BdsAccumulatorService service = new BdsAccumulatorService();

    @Test
    public void testCalculateDeductible_ValidInputs() {
        assertEquals(500.0, service.calculateDeductible(500));
        assertEquals(1000.0, service.calculateDeductible(1000));
        assertEquals(1500.0, service.calculateDeductible(3000));
        assertEquals(2000.0, service.calculateDeductible(5000));
        assertEquals(3000.0, service.calculateDeductible(6000));
        assertEquals(1000.5, service.calculateDeductible(1001)); // Edge case
        assertEquals(1000.0, service.calculateDeductible(1000)); // Edge case
        assertEquals(3000.0, service.calculateDeductible(5001)); // Edge case
    }

    @Test
    public void testCalculateDeductible_NegativeInput() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.calculateDeductible(-1);
        });
        assertEquals("Amount must be non-negative", exception.getMessage());
    }

    @Test
    public void testCalculateDeductible_LargeInput() {
        assertEquals(1000000.0, service.calculateDeductible(1000000)); // Large value
    }
}
