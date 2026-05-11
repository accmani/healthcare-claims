BigDecimal remaining = odsRepo.getDeductibleRemaining(mbrId, planYear);
if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
  patientOwes = patientOwes.add(remaining);
}