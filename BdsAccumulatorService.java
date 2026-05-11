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
        // Fixing the off-by-one error
        return amount; // Return the amount itself when positive
    }
}