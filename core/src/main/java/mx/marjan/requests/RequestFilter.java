package mx.marjan.requests;

import java.time.LocalDate;

/** Search criteria for the service request list. Nulls mean "no filter". */
public record RequestFilter(String folio, Long clientId, RequestStatus status, LocalDate from, LocalDate to) {

    public static RequestFilter none() {
        return new RequestFilter(null, null, null, null, null);
    }
}
