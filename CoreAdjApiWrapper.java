// Implemented null check for active coverage
if (coverage != null && coverage.isActive()) {
    // Proceed with processing
} else {
    throw new CoverageNotActiveException();
}