package mx.marjan.requests;

import java.util.List;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

/** Use cases around a request's packages: list, replace the set, record receipts. */
public class CargoPackageService {

    private final CargoPackageRepository packages = new CargoPackageRepository();

    public List<CargoPackage> list(long requestId) {
        return packages.listByRequest(requestId);
    }

    public Result<Void> replace(long requestId, List<CargoPackage> lines) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para modificar solicitudes");
        }
        return packages.replace(requestId, lines, Session.userId());
    }

    /** FR-DEL-2: per-unit tracking recorded when the trip is delivered. */
    public Result<Void> saveReceipts(List<CargoPackage> lines) {
        if (!Session.has(Permissions.DELIVERIES_WRITE)) {
            return Result.err("No tiene permiso para registrar entregas");
        }
        return packages.saveReceipts(lines);
    }
}
