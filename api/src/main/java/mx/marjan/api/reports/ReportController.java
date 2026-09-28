package mx.marjan.api.reports;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.api.error.ApiProblemException;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.reports.CsvExporter;
import mx.marjan.reports.Report;
import mx.marjan.reports.ReportService;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Result;

/** FR-RPT-1..7: run any of the eight reports and export it to CSV. */
@Controller("/api/reports")
@Secured(Permissions.REPORTS_VIEW)
public class ReportController {

    @Get("/{kind}")
    public HttpResponse<?> report(Authentication authentication,
            String kind,
            @Nullable @QueryValue LocalDate from,
            @Nullable @QueryValue LocalDate to) {
        return Responses.of(run(authentication, kind, from, to));
    }

    @Get(value = "/{kind}/csv", produces = "text/csv")
    public HttpResponse<String> csv(Authentication authentication,
            String kind,
            @Nullable @QueryValue LocalDate from,
            @Nullable @QueryValue LocalDate to) {
        Result<Report> result = run(authentication, kind, from, to);
        if (result.isErr()) {
            return HttpResponse.unprocessableEntity().body(String.join("\n", result.problems()));
        }
        try {
            StringWriter writer = new StringWriter();
            CsvExporter.write(result.value(), writer);
            return HttpResponse.ok(writer.toString())
                    .contentType(new MediaType("text/csv", "UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + result.value().title().replace(' ', '_') + ".csv\"");
        } catch (IOException failure) {
            throw new ApiProblemException(List.of("No se pudo generar el CSV"));
        }
    }

    private Result<Report> run(Authentication authentication, String kind, LocalDate from, LocalDate to) {
        ReportService reports = new ReportService(Callers.forAuthentication(authentication));
        LocalDate start = from != null ? from : Dates.today().withDayOfYear(1);
        LocalDate end = to != null ? to : Dates.today();
        return switch (kind) {
            case "revenue" -> reports.revenueByClient(start, end);
            case "routes" -> reports.routeUsage(start, end);
            case "vehicles" -> reports.vehicleUsage(start, end);
            case "fuel" -> reports.fuelEfficiency(start, end);
            case "profitability" -> reports.profitability(start, end);
            case "receivables" -> reports.receivables();
            case "licenses" -> reports.expiringLicenses(Dates.today());
            case "maintenance" -> reports.maintenanceDue(Dates.today());
            default -> throw new ApiProblemException(List.of("Reporte desconocido: " + kind));
        };
    }
}
