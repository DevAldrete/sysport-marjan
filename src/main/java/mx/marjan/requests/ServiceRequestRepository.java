package mx.marjan.requests;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the service-request stored procedures. */
public class ServiceRequestRepository {

    private ServiceRequest map(ResultSet rs) throws SQLException {
        return new ServiceRequest(
                rs.getLong("id"),
                rs.getString("folio"),
                rs.getLong("client_id"),
                rs.getString("client_name"),
                rs.getLong("route_id"),
                rs.getString("route_label"),
                rs.getString("cargo_description"),
                rs.getBigDecimal("estimated_weight"),
                rs.getObject("pickup_date_scheduled", LocalDateTime.class),
                rs.getObject("delivery_date_scheduled", LocalDateTime.class),
                rs.getBigDecimal("agreed_rate"),
                rs.getBoolean("requires_documents"),
                RequestStatus.fromDb(rs.getString("status")),
                rs.getString("notes"),
                rs.getObject("created_at", LocalDateTime.class));
    }

    public List<ServiceRequest> search(RequestFilter filter) {
        String folio = filter.folio() == null || filter.folio().isBlank() ? null : filter.folio().trim();
        Long clientId = filter.clientId();
        String status = filter.status() == null ? null : filter.status().dbValue();
        LocalDateTime from = filter.from() == null ? null : filter.from().atStartOfDay();
        LocalDateTime to = filter.to() == null ? null : filter.to().atTime(23, 59, 59);
        return Database.callList("{call sp_requests_search(?,?,?,?,?)}",
                this::map, folio, clientId, status, from, to);
    }

    public Optional<ServiceRequest> findById(long id) {
        return Database.callOne("{call sp_request_by_id(?)}", this::map, id);
    }

    public List<ServiceRequest> listByStatus(RequestStatus status) {
        return Database.callList("{call sp_requests_by_status(?)}", this::map, status.dbValue());
    }

    /** FR-INV-1: authorized rates without an invoice yet, so collections can see what is billable. */
    public List<ServiceRequest> listPendingBilling() {
        return Database.callList("{call sp_requests_pending_billing()}", this::map);
    }

    public Result<Long> create(ServiceRequest request, long userId) {
        return Database.callForId("{call sp_request_create(?,?,?,?,?,?,?,?,?,?,?,?)}",
                request.clientId(), request.routeId(), request.cargoDescription(),
                request.estimatedWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(), request.notes(), userId);
    }

    public Result<Void> update(ServiceRequest request, long userId) {
        return Database.callVoid("{call sp_request_update(?,?,?,?,?,?,?,?,?,?,?,?)}",
                request.id(), request.clientId(), request.routeId(), request.cargoDescription(),
                request.estimatedWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(),
                request.notes(), userId);
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_request_delete(?,?)}", id);
    }

    /** BR-03 + BR-04: authorize only from requested, with a positive rate. */
    public Result<Void> authorize(long id, BigDecimal rate, long userId) {
        return Database.callVoid("{call sp_authorize_request(?,?,?,?)}", id, rate, userId);
    }

    /** BR-03: schedule only from authorized; delivery must be after pickup. */
    public Result<Void> schedule(long id, LocalDateTime pickup, LocalDateTime delivery, long userId) {
        return Database.callVoid("{call sp_schedule_request(?,?,?,?,?)}",
                id, pickup, delivery, userId);
    }

    /** BR-03: cancel a request that has not started; a reason is required. */
    public Result<Void> cancel(long id, String reason, long userId) {
        return Database.callVoid("{call sp_cancel_request(?,?,?,?)}", id, reason, userId);
    }

    /** FR-DEL-2 / BR-13: close a delivered request only when its delivery is complete. */
    public Result<Void> close(long id, long userId) {
        return Database.callVoid("{call sp_close_request(?,?,?)}", id, userId);
    }
}
