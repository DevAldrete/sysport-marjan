package mx.marjan.reports;

public record DashboardAlerts(
        int expiringLicenses,
        int overdueInvoices,
        int maintenanceDue,
        int pendingAssignments) {}
