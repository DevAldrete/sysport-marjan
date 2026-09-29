package mx.marjan.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FR-DSH-4: a client with an outstanding balance. */
public record Debtor(
        String clientName,
        BigDecimal balance,
        int invoices,
        LocalDate oldestDue) {}
