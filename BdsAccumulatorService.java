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
    public int calculateDeductible(int amount) {
        // Validate input
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        // Implement actual deductible calculation logic
        if (amount <= 1000) {
            return amount; // Full amount is deductible
        } else if (amount <= 5000) {
            return 1000 + (amount - 1000) / 2; // 50% of the amount over 1000
        } else {
            return 3000 + (amount - 5000) / 3; // 33% of the amount over 5000
        }
    }
}