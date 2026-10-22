package dev.requestportal.org.department;

public record DepartmentResponse(Long id, String code, String name, String email, Long version) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(), department.getCode(), department.getName(), department.getEmail(),
                department.getVersion());
    }
}
