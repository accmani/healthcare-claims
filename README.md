# Healthcare Claims Adjudication System

An enterprise-grade Spring Boot application for real-time healthcare claims adjudication, supporting HMO, PPO, EPO, and HDHP insurance plan types. This system implements standard payer-side claims processing logic aligned with ANSI X12 837/835 transaction workflows.

---

## Architecture Overview

```
healthcare-claims/
├── src/main/java/com/enterprise/healthcare/claims/
│   ├── ClaimsApplication.java              # Spring Boot entry point
│   ├── model/
│   │   ├── Patient.java                    # Member/patient entity
│   │   ├── Coverage.java                   # Insurance coverage plan entity
│   │   ├── Provider.java                   # Healthcare provider entity
│   │   ├── ClaimLine.java                  # Individual service line items
│   │   ├── Claim.java                      # Claim header and aggregate root
│   │   └── AdjudicationResult.java         # Value object: adjudication outcome
│   ├── repository/
│   │   └── ClaimRepository.java            # Spring Data JPA repository
│   ├── service/
│   │   ├── EligibilityService.java         # Member eligibility verification
│   │   ├── DeductibleCalculatorService.java # Deductible/OOP max calculations
│   │   └── ClaimAdjudicationService.java   # Core adjudication engine
│   └── controller/
│       └── ClaimController.java            # REST API controller
└── src/test/java/.../service/
    └── ClaimAdjudicationServiceTest.java   # Unit tests (includes failing tests)
```

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| `EligibilityService` | Validates member eligibility: active coverage, effective dates |
| `DeductibleCalculatorService` | Calculates deductible application, coinsurance, OOP max enforcement |
| `ClaimAdjudicationService` | Orchestrates full adjudication workflow; produces `AdjudicationResult` |
| `ClaimController` | Exposes REST endpoints; maps HTTP to service layer |
| `ClaimRepository` | Persistence layer; custom queries for reporting and audit |

---

## Known Issues

The following bugs have been identified and are tracked in the issue backlog. Tests in `ClaimAdjudicationServiceTest` demonstrate each bug:

### [BUG-001] NullPointerException when patient has no active coverage

**Severity:** Critical
**File:** `ClaimAdjudicationService.java` (method: `adjudicateClaim`)
**Symptom:** `NullPointerException` at runtime when a patient record has `coverage == null`
**Root Cause:** The method accesses `claim.getPatient().getCoverage().getCoverageType()` without null-checking `getCoverage()` first. Even though `EligibilityService.checkEligibility()` correctly handles null coverage, the coverage object is accessed again unconditionally after the eligibility check.
**Failing Test:** `testAdjudicateClaimWithNullCoverage()`
**Fix:** Add a null guard before accessing coverage type:
```java
if (claim.getPatient().getCoverage() == null) {
    return AdjudicationResult.denied(claim.getClaimNumber(), "DENIAL-003: No active coverage on file");
}
```

---

### [BUG-002] Off-by-one error in deductible threshold comparison causes patient overbilling

**Severity:** High
**File:** `DeductibleCalculatorService.java` (method: `calculatePatientResponsibility`)
**Symptom:** When a patient's year-to-date deductible payments exactly equal the deductible amount, the system incorrectly charges them the full claim amount instead of only coinsurance.
**Root Cause:** The comparison `yearToDatePaid.compareTo(deductibleAmount) > 0` uses strict greater-than. When `yearToDatePaid == deductibleAmount`, the deductible is fully satisfied, but the condition evaluates to `false`, causing the full deductible logic to re-apply.
**Failing Test:** `testDeductibleAtExactLimit()`
**Fix:** Change `> 0` to `>= 0`:
```java
// Before (buggy):
if (yearToDatePaid.compareTo(deductibleAmount) > 0) {

// After (correct):
if (yearToDatePaid.compareTo(deductibleAmount) >= 0) {
```

---

### [BUG-003] ConcurrentModificationException during claim line validation

**Severity:** High
**File:** `ClaimAdjudicationService.java` (method: `validateClaimLines`)
**Symptom:** `java.util.ConcurrentModificationException` is thrown whenever a claim contains at least one line with a null or zero billed amount.
**Root Cause:** The `validateClaimLines` method uses a for-each loop and calls `claimLines.remove(line)` inside the loop body. The Java `ArrayList` iterator tracks structural modification counts; calling `remove()` on the list (not the iterator) increments the mod count, causing the iterator to throw `ConcurrentModificationException` on the next iteration.
**Failing Test:** `testClaimLineValidation()`
**Fix:** Use `Iterator.remove()` or `List.removeIf()`:
```java
// Option A: Iterator
Iterator<ClaimLine> iterator = claimLines.iterator();
while (iterator.hasNext()) {
    ClaimLine line = iterator.next();
    if (line.getBilledAmount() == null || line.getBilledAmount().compareTo(BigDecimal.ZERO) <= 0) {
        iterator.remove(); // Safe
    }
}

// Option B: removeIf (preferred - Java 8+)
claimLines.removeIf(line ->
    line.getBilledAmount() == null ||
    line.getBilledAmount().compareTo(BigDecimal.ZERO) <= 0);
```

---

## Setup Instructions

### Prerequisites

- Java 17 or higher
- Maven 3.8+

### Running the Application

```bash
# Clone the repository
git clone https://github.com/enterprise/healthcare-claims.git
cd healthcare-claims

# Build the project
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run
```

The application starts on **http://localhost:8080**

### Running Tests

```bash
# Run all tests (expect 3 failures due to known bugs)
mvn test

# Run a specific test class
mvn test -Dtest=ClaimAdjudicationServiceTest

# Run only passing tests
mvn test -Dtest="ClaimAdjudicationServiceTest#testAdjudicateClaimWithValidCoverage+testDeductibleCalculation+testDeductiblePartiallyMet+testOutOfPocketMaxReached"
```

**Expected test results:**

| Test | Expected Result | Reason |
|------|----------------|--------|
| `testAdjudicateClaimWithValidCoverage` | PASS | Happy path |
| `testAdjudicateClaimForIneligibleMember` | PASS | Eligibility denial |
| `testDeductibleCalculation` | PASS | Normal deductible |
| `testDeductiblePartiallyMet` | PASS | Partial deductible |
| `testOutOfPocketMaxReached` | PASS | OOP max enforcement |
| `testAdjudicateClaimWithNullCoverage` | FAIL (NPE) | Bug #1 |
| `testDeductibleAtExactLimit` | FAIL (assertion) | Bug #2 |
| `testClaimLineValidation` | FAIL (CME) | Bug #3 |

---

## API Endpoints

Base URL: `http://localhost:8080/api/claims`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/claims` | Submit a new healthcare claim |
| `GET` | `/api/claims` | List all claims (optional `?status=PENDING`) |
| `GET` | `/api/claims/{claimNumber}` | Get claim status and details |
| `POST` | `/api/claims/{claimNumber}/adjudicate` | Trigger adjudication for a PENDING claim |

### Interactive Documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/api-docs
- **H2 Console:** http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:claimsdb`)

### Example: Submit a Claim

```bash
curl -X POST http://localhost:8080/api/claims \
  -H "Content-Type: application/json" \
  -d '{
    "claimNumber": "CLM-20240615-001",
    "submissionDate": "2024-06-15",
    "status": "PENDING",
    "patient": {
      "memberId": "MBR-001",
      "firstName": "John",
      "lastName": "Doe",
      "dateOfBirth": "1985-06-15",
      "coverage": {
        "coverageType": "PPO",
        "deductibleAmount": 1000.00,
        "outOfPocketMax": 5000.00,
        "copayAmount": 30.00,
        "effectiveDate": "2024-01-01",
        "active": true
      }
    },
    "provider": {
      "npi": "1234567890",
      "name": "Dr. Jane Smith, MD",
      "specialty": "Internal Medicine",
      "networkStatus": "IN_NETWORK",
      "taxId": "123456789"
    },
    "claimLines": [
      {
        "procedureCode": "99213",
        "diagnosisCode": "J06.9",
        "serviceDate": "2024-06-15",
        "billedAmount": 250.00,
        "quantity": 1
      }
    ],
    "totalBilledAmount": 250.00
  }'
```

### Example: Adjudicate a Claim

```bash
curl -X POST http://localhost:8080/api/claims/CLM-20240615-001/adjudicate
```

---

## Business Rules

| Plan Type | Network Requirement | Referral Required | Deductible | After Deductible |
|-----------|--------------------|--------------------|------------|-----------------|
| HMO | In-network only | Yes | Yes | Copay per visit |
| PPO | In/out-of-network | No | Yes | Coinsurance (20%) |
| EPO | In-network only | No | Yes | Copay per visit |
| HDHP | In/out-of-network | No | High deductible | Coinsurance after deductible |

**Allowed Amount Calculation:**
- In-network: 85% of billed charges (negotiated rate proxy)
- Out-of-network: 60% of billed charges (UCR rate proxy)

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2.0 |
| Language | Java 17 |
| Persistence | Spring Data JPA / Hibernate |
| Database | H2 (in-memory, for demo) |
| API Docs | SpringDoc OpenAPI 2.3.0 (Swagger) |
| Build Tool | Maven |
| Boilerplate | Lombok |
| Testing | JUnit 5 / Mockito |
