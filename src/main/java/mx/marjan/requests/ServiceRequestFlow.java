package mx.marjan.requests;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.Result;

/** Pure transitions over a ServiceRequest. No I/O, no clock (dates are passed in). */
public final class ServiceRequestFlow {

    private ServiceRequestFlow() {}

    /** BR-03 + BR-04: authorize only from requested, with a positive agreed rate snapshot. */
    public static Result<ServiceRequest> authorize(ServiceRequest request, BigDecimal agreedRate) {
        List<String> problems = transitionProblems(request, RequestStatus.AUTHORIZED);
        if (agreedRate == null || agreedRate.signum() <= 0) {
            problems.add("La tarifa acordada debe ser mayor a cero");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        return Result.ok(request.withAgreedRate(agreedRate).withStatus(RequestStatus.AUTHORIZED));
    }

    /** Both scheduled dates are required and delivery must be after pickup. */
    public static Result<ServiceRequest> schedule(ServiceRequest request,
            LocalDateTime pickup, LocalDateTime delivery) {
        List<String> problems = transitionProblems(request, RequestStatus.SCHEDULED);
        if (pickup == null || delivery == null) {
            problems.add("Las fechas programadas de recoleccion y entrega son obligatorias");
        } else if (!delivery.isAfter(pickup)) {
            problems.add("La fecha de entrega debe ser posterior a la de recoleccion");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        return Result.ok(request.withSchedule(pickup, delivery).withStatus(RequestStatus.SCHEDULED));
    }

    public static Result<ServiceRequest> cancel(ServiceRequest request, String reason) {
        List<String> problems = transitionProblems(request, RequestStatus.CANCELLED);
        if (reason == null || reason.isBlank()) {
            problems.add("Debe indicar el motivo de la cancelacion");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        return Result.ok(request.withNotes(reason).withStatus(RequestStatus.CANCELLED));
    }

    public static Result<ServiceRequest> moveTo(ServiceRequest request, RequestStatus next) {
        List<String> problems = transitionProblems(request, next);
        return problems.isEmpty() ? Result.ok(request.withStatus(next)) : Result.err(problems);
    }

    private static List<String> transitionProblems(ServiceRequest request, RequestStatus next) {
        List<String> problems = new ArrayList<>();
        if (!request.status().canMoveTo(next)) {
            problems.add("No se puede pasar de " + request.status().label() + " a " + next.label());
        }
        return problems;
    }
}
