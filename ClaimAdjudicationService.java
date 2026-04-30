if (deductibleRemaining.compareTo(BigDecimal.ZERO) >= 0) {
  patientOwes = patientOwes.add(deductibleRemaining);
}