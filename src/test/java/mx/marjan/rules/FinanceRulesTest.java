package mx.marjan.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import mx.marjan.clients.PaymentTerms;
import mx.marjan.finance.AdvanceBalance;
import mx.marjan.finance.AdvanceRules;
import mx.marjan.finance.Expense;
import mx.marjan.finance.ExpenseRules;
import mx.marjan.finance.ExpenseType;
import mx.marjan.finance.InvoiceRules;
import mx.marjan.finance.InvoiceStatus;
import mx.marjan.fleet.FuelLoad;
import mx.marjan.fleet.FuelRules;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceRulesTest {

  @Test
  void advanceBalanceOperatorOwes() { // BR-16
    AdvanceBalance balance = AdvanceRules.balance(
        new BigDecimal("5000"), new BigDecimal("3000"), new BigDecimal("1000"));
    assertTrue(balance.outcome() instanceof AdvanceBalance.OperatorOwes);
  }

  @Test
  void advanceBalanceCompanyOwes() { // BR-16
    AdvanceBalance balance = AdvanceRules.balance(
        new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500"));
    assertTrue(balance.outcome() instanceof AdvanceBalance.CompanyOwes);
  }

  @Test
  void advanceBalanceSettled() { // BR-16
    AdvanceBalance balance = AdvanceRules.balance(
        new BigDecimal("4000"), new BigDecimal("3000"), new BigDecimal("1000"));
    assertTrue(balance.outcome() instanceof AdvanceBalance.Settled);
  }

  @Test
  void expenseMustBePositive() { // BR-17
    Expense expense = new Expense(0, 1, "", ExpenseType.TOLLS, BigDecimal.ZERO, LocalDate.now(), "");
    assertTrue(ExpenseRules.validate(expense).isErr());
  }

  @Test
  void fuelAmountMustMatchLitersTimesPrice() { // BR-18
    FuelLoad load = new FuelLoad(0, 1, "", null, "", "", LocalDateTime.now(),
        new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("9999"), null);
    assertTrue(FuelRules.validate(load, BigDecimal.ZERO).isErr());
  }

  @Test
  void fuelOdometerCannotDecrease() { // BR-18
    FuelLoad load = new FuelLoad(0, 1, "", null, "", "", LocalDateTime.now(),
        new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), new BigDecimal("50"));
    assertTrue(FuelRules.validate(load, new BigDecimal("100")).isErr());
  }

  @Test
  void validFuelLoadPasses() { // BR-18
    FuelLoad load = new FuelLoad(0, 1, "", null, "", "", LocalDateTime.now(),
        new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), new BigDecimal("150"));
    assertTrue(FuelRules.validate(load, new BigDecimal("100")).isOk());
  }

  @Test
  void invoiceStatusIsDerived() { // BR-19
    assertEquals(InvoiceStatus.PAID, InvoiceRules.deriveStatus(InvoiceStatus.PENDING,
        new BigDecimal("1000"), new BigDecimal("1000"), LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1)));
    assertEquals(InvoiceStatus.OVERDUE, InvoiceRules.deriveStatus(InvoiceStatus.PENDING,
        new BigDecimal("1000"), new BigDecimal("0"), LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1)));
    assertEquals(InvoiceStatus.PENDING, InvoiceRules.deriveStatus(InvoiceStatus.PENDING,
        new BigDecimal("1000"), new BigDecimal("0"), LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 2, 1)));
  }

  @Test
  void paymentsCannotExceedBalance() { // BR-19
    assertTrue(InvoiceRules.canPay(new BigDecimal("1000"), new BigDecimal("800"),
        new BigDecimal("300")).isErr());
    assertTrue(InvoiceRules.canPay(new BigDecimal("1000"), new BigDecimal("800"),
        new BigDecimal("200")).isOk());
  }

  @Test
  void onlyDeliveredOrClosedCanBeInvoiced() { // BR-20
    assertTrue(InvoiceRules.canInvoice(request(RequestStatus.DELIVERED)).isOk());
    assertTrue(InvoiceRules.canInvoice(request(RequestStatus.CLOSED)).isOk());
    assertTrue(InvoiceRules.canInvoice(request(RequestStatus.IN_TRANSIT)).isErr());
  }

  @Test
  void dueDateDependsOnPaymentTerms() { // BR-19
    LocalDate issue = LocalDate.of(2026, 1, 1);
    assertEquals(issue, InvoiceRules.dueDate(issue, PaymentTerms.CASH, 0));
    assertEquals(LocalDate.of(2026, 1, 31), InvoiceRules.dueDate(issue, PaymentTerms.CREDIT, 30));
  }

  private ServiceRequest request(RequestStatus status) {
    return new ServiceRequest(1, "SR-2026-000001", 1, "Cliente", 1, "A -> B", "Carga",
        BigDecimal.TEN, LocalDateTime.now(), LocalDateTime.now().plusDays(1),
        new BigDecimal("1000"), true, status, "", LocalDateTime.now());
  }
}
