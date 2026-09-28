package mx.marjan.requests;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/**
 * Thin JDBC wrapper over the request-package procedures. Packages are saved row
 * by row inside one {@link Database#inTransaction} unit of work, so a request's
 * package list is replaced atomically even though each row is a separate call.
 */
public class CargoPackageRepository {

    private CargoPackage map(ResultSet rs) throws SQLException {
        return new CargoPackage(
                rs.getLong("id"),
                rs.getLong("service_request_id"),
                rs.getInt("line_no"),
                rs.getString("description"),
                rs.getBigDecimal("quantity"),
                PackageUnit.fromDb(rs.getString("unit")),
                rs.getBigDecimal("unit_weight"),
                rs.getBigDecimal("received_quantity"),
                PackageCondition.fromDb(rs.getString("receipt_condition")));
    }

    public List<CargoPackage> listByRequest(long requestId) {
        return Database.callList("{call sp_request_packages(?)}", this::map, requestId);
    }

    /**
     * Replaces the request's packages with {@code packages}: upserts each line
     * and removes the ones no longer present, in one transaction. A new line has
     * {@code id == 0}; existing lines keep their id. Reordering line numbers is
     * collision-free because the table has no unique constraint on line_no.
     */
    public Result<Void> replace(long requestId, List<CargoPackage> packages, long userId) {
        try {
            return Database.inTransaction(connection -> {
                Set<Long> kept = new HashSet<>();
                int line = 1;
                for (CargoPackage cargoPackage : packages) {
                    Result<Long> saved = save(connection, requestId, line, cargoPackage, userId);
                    if (saved.isErr()) {
                        throw new PackageRejected(saved.problems());
                    }
                    kept.add(saved.value());
                    line++;
                }
                for (Long existing : listIds(connection, requestId)) {
                    if (!kept.contains(existing)) {
                        Database.callNoOut(connection, "{call sp_package_delete(?)}", existing);
                    }
                }
                return Result.<Void>ok(null);
            });
        } catch (PackageRejected rejected) {
            return Result.err(rejected.problems);
        }
    }

    /** Records the delivered quantity and condition of each package line. */
    public Result<Void> saveReceipts(List<CargoPackage> packages) {
        try {
            return Database.inTransaction(connection -> {
                for (CargoPackage cargoPackage : packages) {
                    Result<Void> saved = Database.callVoid(connection,
                            "{call sp_package_receipt_save(?,?,?)}", cargoPackage.id(),
                            cargoPackage.receivedQuantity(),
                            cargoPackage.receiptCondition() == null
                                    ? null : cargoPackage.receiptCondition().dbValue());
                    if (saved.isErr()) {
                        throw new PackageRejected(saved.problems());
                    }
                }
                return Result.<Void>ok(null);
            });
        } catch (PackageRejected rejected) {
            return Result.err(rejected.problems);
        }
    }

    private Result<Long> save(Connection connection, long requestId, int line,
            CargoPackage cargoPackage, long userId) {
        return Database.callForId(connection, "{call sp_package_save(?,?,?,?,?,?,?,?,?,?)}",
                cargoPackage.id(), requestId, line, cargoPackage.description(),
                cargoPackage.quantity(),
                cargoPackage.unit() == null ? null : cargoPackage.unit().dbValue(),
                cargoPackage.unitWeight(), userId);
    }

    private List<Long> listIds(Connection connection, long requestId) {
        return Database.callList(connection, "{call sp_request_packages(?)}",
                rs -> rs.getLong("id"), requestId);
    }

    /** Signals a business-rule rejection so the surrounding transaction rolls back. */
    private static final class PackageRejected extends RuntimeException {
        private final transient List<String> problems;

        PackageRejected(List<String> problems) {
            super(String.join("; ", problems));
            this.problems = problems;
        }
    }
}
