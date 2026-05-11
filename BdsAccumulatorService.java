public class BdsAccumulatorService {
    public int calculateDeductible(int amount) {
        // Validate input
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        // Fixing the off-by-one error
        return amount > 0 ? amount - 1 : 0;
    }
}