package mx.marjan.reports;

import java.math.BigDecimal;

/** FR-DSH-5: one month of the revenue/margin trend chart. */
public record MonthlyRevenue(
        String month,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal margin) {}
