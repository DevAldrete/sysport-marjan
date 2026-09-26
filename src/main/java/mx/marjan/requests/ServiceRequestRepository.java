package mx.marjan.requests;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

public class ServiceRequestRepository {

    private static final String BASE = """
            SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
                   CONCAT(r.origin, ' -> ', r.destination) AS route_label,
                   sr.cargo_description, sr.estimated_weight, sr.pickup_date_scheduled,
                   sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
                   sr.status, sr.notes, sr.created_at
            FROM service_requests sr
            JOIN clients c ON c.id = sr.client_id
            JOIN routes r ON r.id = sr.route_id
            """;

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
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (filter.folio() != null && !filter.folio().isBlank()) {
            conditions.add("sr.folio LIKE ?");
            params.add("%" + filter.folio().trim() + "%");
        }
        if (filter.clientId() != null) {
            conditions.add("sr.client_id = ?");
            params.add(filter.clientId());
        }
        if (filter.status() != null) {
            conditions.add("sr.status = ?");
            params.add(filter.status().dbValue());
        }
        if (filter.from() != null) {
            conditions.add("sr.pickup_date_scheduled >= ?");
            params.add(filter.from().atStartOfDay());
        }
        if (filter.to() != null) {
            conditions.add("sr.pickup_date_scheduled <= ?");
            params.add(filter.to().atTime(23, 59, 59));
        }
        String sql = BASE
                + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                + " ORDER BY sr.created_at DESC";
        return Database.queryList(sql, this::map, params.toArray());
    }

    public Optional<ServiceRequest> findById(long id) {
        return Database.queryOne(BASE + " WHERE sr.id = ?", this::map, id);
    }

    public Optional<ServiceRequest> findById(java.sql.Connection connection, long id) throws SQLException {
        return Database.queryOne(connection, BASE + " WHERE sr.id = ?", this::map, id);
    }

    public List<ServiceRequest> listByStatus(RequestStatus status) {
        return Database.queryList(BASE + " WHERE sr.status = ? ORDER BY sr.pickup_date_scheduled",
                this::map, status.dbValue());
    }

    /** Requests waiting to be scheduled by the lifecycle sweep. */
    public List<ServiceRequest> listAuthorized(java.sql.Connection connection) throws SQLException {
        return Database.queryList(connection, BASE + " WHERE sr.status = 'authorized'", this::map);
    }

    /** FR-INV-1: authorized rates without an invoice yet, so collections can see what is billable. */
    public List<ServiceRequest> listPendingBilling() {
        return Database.queryList(BASE + """
                WHERE sr.agreed_rate IS NOT NULL AND sr.agreed_rate > 0
                  AND sr.status <> 'cancelled'
                  AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.service_request_id = sr.id)
                ORDER BY sr.created_at DESC
                """, this::map);
    }

    public long nextSequence(int year) {
        return Database.queryOne("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(folio, 9) AS UNSIGNED)), 0) + 1 AS next_seq
                FROM service_requests WHERE folio LIKE ?
                """, rs -> rs.getLong("next_seq"), "SR-" + year + "-%").orElse(1L);
    }

    public long insert(java.sql.Connection connection, ServiceRequest request, long userId) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "service_requests");
        Database.update(connection, """
                INSERT INTO service_requests
                  (id, folio, client_id, route_id, cargo_description, estimated_weight,
                   pickup_date_scheduled, delivery_date_scheduled, agreed_rate,
                   requires_documents, status, notes, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, request.folio(), request.clientId(), request.routeId(), request.cargoDescription(),
                request.estimatedWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(), request.status().dbValue(),
                request.notes(), userId, userId);
        return id;
    }

    public void delete(java.sql.Connection connection, long id) throws SQLException {
        Database.update(connection, "DELETE FROM service_requests WHERE id = ?", id);
    }

    public void update(ServiceRequest request, long userId) {
        Database.update("""
                UPDATE service_requests SET
                  client_id = ?, route_id = ?, cargo_description = ?, estimated_weight = ?,
                  pickup_date_scheduled = ?, delivery_date_scheduled = ?, agreed_rate = ?,
                  requires_documents = ?, status = ?, notes = ?, updated_by = ?
                WHERE id = ?
                """,
                request.clientId(), request.routeId(), request.cargoDescription(),
                request.estimatedWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(), request.status().dbValue(),
                request.notes(), userId, request.id());
    }

    public void update(java.sql.Connection connection, ServiceRequest request, long userId) throws SQLException {
        Database.update(connection, """
                UPDATE service_requests SET
                  client_id = ?, route_id = ?, cargo_description = ?, estimated_weight = ?,
                  pickup_date_scheduled = ?, delivery_date_scheduled = ?, agreed_rate = ?,
                  requires_documents = ?, status = ?, notes = ?, updated_by = ?
                WHERE id = ?
                """,
                request.clientId(), request.routeId(), request.cargoDescription(),
                request.estimatedWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(), request.status().dbValue(),
                request.notes(), userId, request.id());
    }
}
