package dev.requestportal.org.department;

import dev.requestportal.web.ConflictException;
import dev.requestportal.web.NotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departments;

    public DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departments.findAllByOrderByCodeAsc().stream().map(DepartmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long id) {
        return DepartmentResponse.from(find(id));
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (departments.existsByCode(request.code())) {
            throw new ConflictException("Department code " + request.code() + " is already in use");
        }
        Department department = departments.save(new Department(request.code(), request.name().strip()));
        return DepartmentResponse.from(department);
    }

    @Transactional
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department department = find(id);
        if (!Objects.equals(department.getVersion(), request.version())) {
            throw new ConflictException("Department " + department.getCode()
                    + " was changed by someone else; reload it and try again");
        }
        department.rename(request.name().strip());
        departments.flush();
        return DepartmentResponse.from(department);
    }

    @Transactional(readOnly = true)
    public Department find(Long id) {
        return departments.findById(id).orElseThrow(() -> new NotFoundException("Department " + id + " not found"));
    }
}
