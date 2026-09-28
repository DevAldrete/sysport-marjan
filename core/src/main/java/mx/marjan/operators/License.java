package mx.marjan.operators;

import java.time.LocalDate;

public record License(
        long id,
        String licenseNumber,
        String licenseType,
        LocalDate issueDate,
        LocalDate expirationDate) {

    public static License empty() {
        return new License(0, "", "", null, null);
    }
}
