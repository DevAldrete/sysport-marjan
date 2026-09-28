package mx.marjan.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests against the real MySQL from docker-compose, because the
 * business rules live in the database (the single source of truth).
 *
 * <p>They are opt-in so {@code mvn test} stays fast and does not touch the dev
 * database. Run them against a freshly seeded database:
 *
 * <pre>
 *   docker compose down -v &amp;&amp; docker compose up -d
 *   SYSPORT_IT=1 mvn test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")
class SqlRulesTest {

    private static Connection connection;

    @BeforeAll
    static void connect() throws Exception {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        String name = env("DB_NAME", "sysportdb");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?sslMode=DISABLED&allowPublicKeyRetrieval=true";
        connection = DriverManager.getConnection(url, env("DB_USER", "marjan"), env("DB_PASSWORD", "changeme"));
    }

    @Test
    void fullLifecycleWithDocumentsPaysAndRejectsOverpayment() throws Exception {
        long request = createRequest(2, 4, "2027-01-05 08:00:00", "2027-01-06 18:00:00",
                new BigDecimal("10000"), true);
        authorize(request, new BigDecimal("10000"));
        schedule(request, "2027-01-05 08:00:00", "2027-01-06 18:00:00");
        long trip = assign(request, 5, 8);
        assertNull(depart(trip));
        assertNull(arrive(trip, new BigDecimal("100.0")));
        assertEquals("in_transit", requestStatus(request), "documents required: not delivered yet");

        assertNull(saveDelivery(trip, "pending_documents"));
        assertNotNull(close(request), "close must be blocked while documents are pending");
        assertNull(saveDelivery(trip, "complete"));
        assertNull(close(request), "close must succeed with a complete delivery");

        long invoice = createInvoice(request, new BigDecimal("10000"));
        assertNull(registerPayment(invoice, new BigDecimal("10000"), localToday()));
        assertEquals("paid", scalarString("SELECT status FROM invoices WHERE id=" + invoice));
        assertNotNull(registerPayment(invoice, new BigDecimal("1"), localToday()),
                "overpayment must be rejected");
    }

    @Test
    void requestWithoutDocumentsClosesAfterArrival() throws Exception {
        long request = createRequest(2, 4, "2027-02-05 08:00:00", "2027-02-06 18:00:00",
                new BigDecimal("9000"), false);
        authorize(request, new BigDecimal("9000"));
        schedule(request, "2027-02-05 08:00:00", "2027-02-06 18:00:00");
        long trip = assign(request, 8, 7);
        assertNull(depart(trip));
        double mileageBefore = scalarDouble("SELECT mileage FROM vehicles WHERE id=8");
        assertNull(arrive(trip, new BigDecimal("150.0")));
        assertEquals("delivered", requestStatus(request), "no documents: arrival delivers the request");
        assertEquals(mileageBefore + 150.0,
                scalarDouble("SELECT mileage FROM vehicles WHERE id=8"), 0.001, "BR-21 mileage accumulates");
        assertNull(close(request), "a delivered no-documents request closes without a delivery");
    }

    @Test
    void paymentOnCancelledInvoiceIsRejected() throws Exception {
        long request = createRequest(2, 4, "2027-03-01 08:00:00", "2027-03-02 18:00:00",
                new BigDecimal("5000"), true);
        long invoiceId = 9000 + request;
        exec("INSERT INTO invoices (id, client_id, service_request_id, invoice_number, amount, "
                + "issue_date, due_date, status, created_by) VALUES (" + invoiceId + ", 2, " + request
                + ", 'INV-TEST-" + invoiceId + "', 5000, CURDATE(), CURDATE(), 'cancelled', 1)");
        assertNotNull(registerPayment(invoiceId, new BigDecimal("100"), localToday()),
                "a cancelled invoice takes no payments");
    }

    @Test
    void invoiceCancellationRules() throws Exception {
        long pendingRequest = createRequest(2, 4, "2027-03-10 08:00:00", "2027-03-11 18:00:00",
                new BigDecimal("2000"), true);
        long paidRequest = createRequest(2, 4, "2027-03-12 08:00:00", "2027-03-13 18:00:00",
                new BigDecimal("2000"), true);
        long pending = 9100 + pendingRequest;
        long paid = 9200 + paidRequest;
        insertInvoice(pending, pendingRequest, "pending");
        insertInvoice(paid, paidRequest, "paid");
        assertNull(cancelInvoice(pending), "a pending invoice can be cancelled");
        assertEquals("cancelled", scalarString("SELECT status FROM invoices WHERE id=" + pending));
        assertNotNull(cancelInvoice(paid), "a paid invoice cannot be cancelled");
    }

    @Test
    void vehicleCannotBeDoubleBooked() throws Exception {
        long first = createRequest(2, 4, "2027-04-05 08:00:00", "2027-04-06 18:00:00",
                new BigDecimal("1000"), true);
        authorize(first, new BigDecimal("1000"));
        schedule(first, "2027-04-05 08:00:00", "2027-04-06 18:00:00");
        long firstTrip = assign(first, 10, 10);

        long second = createRequest(2, 4, "2027-04-06 08:00:00", "2027-04-07 18:00:00",
                new BigDecimal("1000"), true);
        authorize(second, new BigDecimal("1000"));
        schedule(second, "2027-04-06 08:00:00", "2027-04-07 18:00:00");
        Object problems = call("{call sp_assign_trip(?,?,?,?,?,?)}", new int[] { Types.VARCHAR, Types.BIGINT },
                second, 10L, 10L, 1L)[0];
        assertNotNull(problems, "BR-05: overlapping vehicle window must be rejected");

        // Leave the vehicle free for the next run.
        assertNull(call("{call sp_cancel_trip(?,?,?,?)}", new int[] { Types.VARCHAR },
                firstTrip, "prueba", 1L)[0]);
    }

    @Test
    void reassignmentWorksWhileTripIsScheduled() throws Exception {
        long request = createRequest(2, 4, "2027-08-05 08:00:00", "2027-08-06 18:00:00",
                new BigDecimal("1000"), true);
        authorize(request, new BigDecimal("1000"));
        schedule(request, "2027-08-05 08:00:00", "2027-08-06 18:00:00");
        long trip = assign(request, 5, 7);
        assertEquals("assigned", requestStatus(request), "BR-15: assignment marks the request assigned");

        assertNull(reassign(trip, 6, 10), "BR-15: reassign before departure must succeed");
        assertEquals(6L, scalarLong("SELECT vehicle_id FROM trips WHERE id=" + trip),
                "the trip must point at the new vehicle");
    }

    @Test
    void foliosAreSequential() throws Exception {
        long a = createRequest(2, 4, "2027-05-05 08:00:00", "2027-05-06 18:00:00",
                new BigDecimal("1000"), true);
        long b = createRequest(2, 4, "2027-05-07 08:00:00", "2027-05-08 18:00:00",
                new BigDecimal("1000"), true);
        String folioA = scalarString("SELECT folio FROM service_requests WHERE id=" + a);
        String folioB = scalarString("SELECT folio FROM service_requests WHERE id=" + b);
        assertTrue(folioA.matches("SR-\\d{4}-\\d{6}"));
        assertNotEquals(folioA, folioB);
        assertTrue(folioB.compareTo(folioA) > 0, "BR-01: folios increase");
    }

    @Test
    void overlappingRateAndGuardedDeleteAreRejected() throws Exception {
        exec("DELETE FROM client_rates WHERE client_id = 7 AND route_id = 2");
        assertNull(rateProblems(7, 2, "40000", "2027-01-01", "2027-06-30"));
        assertNotNull(rateProblems(7, 2, "41000", "2027-03-01", null), "BR-04: overlapping rate rejected");
        assertNotNull(deleteProblems(1L), "clients with children cannot be deleted");
    }

    // ------------------------------------------------------------------ helpers

    private long createRequest(int clientId, int routeId, String pickup, String delivery,
            BigDecimal rate, boolean docs) throws Exception {
        Object[] out = call("{call sp_request_create(?,?,?,?,?,?,?,?,?,?,?,?)}",
                new int[] { Types.BIGINT, Types.VARCHAR }, clientId, routeId, "Test", new BigDecimal("1000"),
                pickup, delivery, rate, docs, "test", 1L);
        assertNull(out[1], "request creation should succeed");
        return ((Number) out[0]).longValue();
    }

    private void authorize(long request, BigDecimal rate) throws Exception {
        assertNull(call("{call sp_authorize_request(?,?,?,?)}", new int[] { Types.VARCHAR },
                request, rate, 1L)[0]);
    }

    private void schedule(long request, String pickup, String delivery) throws Exception {
        assertNull(call("{call sp_schedule_request(?,?,?,?,?)}", new int[] { Types.VARCHAR },
                request, pickup, delivery, 1L)[0]);
    }

    private long assign(long request, long vehicle, long operator) throws Exception {
        Object[] out = call("{call sp_assign_trip(?,?,?,?,?,?)}", new int[] { Types.VARCHAR, Types.BIGINT },
                request, vehicle, operator, 1L);
        assertNull(out[0], "assignment should succeed");
        return ((Number) out[1]).longValue();
    }

    private String reassign(long trip, long vehicle, long operator) throws Exception {
        return (String) call("{call sp_reassign_trip(?,?,?,?,?)}", new int[] { Types.VARCHAR },
                trip, vehicle, operator, 1L)[0];
    }

    private String depart(long trip) throws Exception {
        return (String) call("{call sp_depart_trip(?,?,?)}", new int[] { Types.VARCHAR }, trip, 1L)[0];
    }

    private String arrive(long trip, BigDecimal km) throws Exception {
        return (String) call("{call sp_arrive_trip(?,?,?,?)}", new int[] { Types.VARCHAR }, trip, km, 1L)[0];
    }

    private String saveDelivery(long trip, String status) throws Exception {
        return (String) call("{call sp_delivery_save(?,?,?,?,?,?,?,?)}",
                new int[] { Types.BIGINT, Types.VARCHAR },
                trip, "2027-01-06 17:00:00", "Cliente", "PO-1", status, 1L)[1];
    }

    private String close(long request) throws Exception {
        return (String) call("{call sp_close_request(?,?,?)}", new int[] { Types.VARCHAR }, request, 1L)[0];
    }

    private long createInvoice(long request, BigDecimal amount) throws Exception {
        Object[] out = call("{call sp_create_invoice_from_request(?,?,?,?,?,?)}",
                new int[] { Types.VARCHAR, Types.BIGINT }, request, localToday(), amount, 1L);
        assertNull(out[0], "invoice creation should succeed");
        return ((Number) out[1]).longValue();
    }

    private String registerPayment(long invoice, BigDecimal amount, String date) throws Exception {
        return (String) call("{call sp_register_payment(?,?,?,?,?,?)}", new int[] { Types.VARCHAR },
                invoice, amount, date, "cash", 1L)[0];
    }

    private void insertInvoice(long id, long request, String status) throws Exception {
        exec("INSERT INTO invoices (id, client_id, service_request_id, invoice_number, amount, "
                + "issue_date, due_date, status, created_by) VALUES (" + id + ", 2, " + request
                + ", 'INV-TEST-" + id + "', 1000, CURDATE(), CURDATE(), '" + status + "', 1)");
    }

    private String cancelInvoice(long invoice) throws Exception {
        return (String) call("{call sp_cancel_invoice(?,?)}", new int[] { Types.VARCHAR }, invoice)[0];
    }

    private String rateProblems(int clientId, int routeId, String rate, String from, String to) throws Exception {
        return (String) call("{call sp_client_rate_save(?,?,?,?,?,?,?,?)}",
                new int[] { Types.BIGINT, Types.VARCHAR },
                0L, (long) clientId, (long) routeId, new BigDecimal(rate), from, to)[1];
    }

    private String deleteProblems(long id) throws Exception {
        return (String) call("{call sp_client_delete(?,?)}", new int[] { Types.VARCHAR }, id)[0];
    }

    private String requestStatus(long request) throws Exception {
        return scalarString("SELECT status FROM service_requests WHERE id=" + request);
    }

    private String localToday() {
        return java.time.LocalDate.now().toString();
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    private String scalarString(String sql) throws Exception {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private double scalarDouble(String sql) throws Exception {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private long scalarLong(String sql) throws Exception {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private Object[] call(String sql, int[] outTypes, Object... in) throws Exception {
        try (CallableStatement cs = connection.prepareCall(sql)) {
            for (int i = 0; i < in.length; i++) {
                cs.setObject(i + 1, in[i]);
            }
            for (int i = 0; i < outTypes.length; i++) {
                cs.registerOutParameter(in.length + 1 + i, outTypes[i]);
            }
            cs.execute();
            Object[] outs = new Object[outTypes.length];
            for (int i = 0; i < outTypes.length; i++) {
                outs[i] = cs.getObject(in.length + 1 + i);
            }
            return outs;
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
