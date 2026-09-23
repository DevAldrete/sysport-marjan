package mx.marjan.operators;

public record Employee(
        long id,
        String name,
        String address,
        String phone,
        String email,
        String rfc,
        String curp,
        String emergencyContactName,
        String emergencyContactPhone,
        License license,
        EmployeeStatus status) {

    public static Employee empty() {
        return new Employee(0, "", "", "", "", "", "", "", "", null, EmployeeStatus.AVAILABLE);
    }

    @Override
    public String toString() {
        return name;
    }
}
