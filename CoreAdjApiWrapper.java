// Check for active coverage before proceeding
if (coverage != null && coverage.isActive()) {
    // Proceed with processing
} else {
    throw new IllegalStateException("No active coverage available.");
}