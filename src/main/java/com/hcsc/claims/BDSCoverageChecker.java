/* Code changes to fix NullPointerException */
// Example code snippet that resolves the issue
List<Coverage> coverages = getCoverages();
coverages.removeIf(coverage -> coverage.isInactive());
// Additional logic to handle active coverages