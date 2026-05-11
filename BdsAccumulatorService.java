public class BdsAccumulatorService {
    public int calculateDeductible(int amount) {
        // Assuming the deductible is calculated based on some logic
        // Fixing the off-by-one error
        return amount > 0 ? amount - 1 : 0;
    }
}