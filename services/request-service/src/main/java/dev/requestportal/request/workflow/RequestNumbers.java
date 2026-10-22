package dev.requestportal.request.workflow;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Hands out request numbers per department (ACAD-0001, ACAD-0002, ...). The upsert locks the department's
 * row until the transaction ends, so concurrent submissions never get the same number, and a rolled-back
 * submission gives its number back.
 */
@Component
class RequestNumbers {

    private final JdbcClient jdbc;

    RequestNumbers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    String nextCode(String departmentCode) {
        int number = jdbc.sql("""
                        insert into request_number (department_code, last_number) values (:department, 1)
                        on conflict (department_code) do update set last_number = request_number.last_number + 1
                        returning last_number
                        """)
                .param("department", departmentCode)
                .query(Integer.class)
                .single();
        return "%s-%04d".formatted(departmentCode, number);
    }
}
