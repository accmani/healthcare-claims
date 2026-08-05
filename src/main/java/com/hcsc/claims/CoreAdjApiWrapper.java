Coverage cov = db2Repo.findActiveCoverage(claim.getMbrId());
if (cov == null) { return AdjResult.denied("No active coverage: " + claim.getMbrId()); }
if (!cov.isActive()) { return AdjResult.denied("Inactive coverage"); }