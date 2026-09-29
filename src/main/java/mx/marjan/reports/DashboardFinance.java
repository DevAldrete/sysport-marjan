package mx.marjan.reports;

import java.math.BigDecimal;

/** FR-DSH-2: current-month finance summary and outstanding balances. */
public record DashboardFinance(
        BigDecimal monthRevenue,
        BigDecimal monthCollected,
        BigDecimal receivable,
        BigDecimal overdue) {}
