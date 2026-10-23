package dev.requestportal.request.report;

import dev.requestportal.request.workflow.RequestFilter;
import dev.requestportal.request.workflow.RequestQueryService;
import dev.requestportal.request.workflow.RequestStep;
import dev.requestportal.request.workflow.ServiceRequest;
import dev.requestportal.request.workflow.ServiceRequestRepository;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.BusinessRuleException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exports and statistics for staff and admins, with the same visibility as the request list. */
@Service
public class ReportService {

    static final int MAX_EXPORT_ROWS = 50_000;
    private static final int PAGE_SIZE = 500;

    private final ServiceRequestRepository requests;
    private final RequestQueryService queries;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    ReportService(ServiceRequestRepository requests, RequestQueryService queries, JdbcClient jdbc, Clock clock,
            ZoneId portalZone) {
        this.requests = requests;
        this.queries = queries;
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = portalZone;
    }

    /** The filtered request list as an .xlsx workbook, read page by page. */
    @Transactional(readOnly = true)
    public byte[] exportRequests(CurrentUser user, RequestFilter filter) {
        Optional<Specification<ServiceRequest>> spec = queries.scoped(user, filter);
        Instant now = clock.instant();
        try (ExcelWorkbook workbook = new ExcelWorkbook(zone)) {
            workbook.sheet("Requests",
                    new String[] {"Code", "Title", "Requester", "Email", "Status", "Current step", "Department",
                            "Due", "Overdue", "Submitted", "Finished", "Result collected"},
                    new int[] {12, 32, 24, 30, 12, 34, 12, 17, 9, 17, 17, 16});
            if (spec.isPresent()) {
                Page<ServiceRequest> page;
                int pageNumber = 0;
                do {
                    page = requests.findAll(spec.get(),
                            PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "submittedAt")));
                    if (page.getTotalElements() > MAX_EXPORT_ROWS) {
                        throw new BusinessRuleException("The export would have " + page.getTotalElements()
                                + " rows; narrow the filter to at most " + MAX_EXPORT_ROWS);
                    }
                    for (ServiceRequest request : page) {
                        Optional<RequestStep> step = request.currentStep();
                        workbook.row(
                                request.getCode(),
                                request.getTitle(),
                                request.getRequesterName(),
                                request.getRequesterEmail(),
                                request.getStatus().name(),
                                step.map(s -> s.getPosition() + ". " + s.getDescription()).orElse(null),
                                step.map(RequestStep::getDepartmentCode).orElse(null),
                                step.map(RequestStep::getDueAt).orElse(null),
                                step.map(s -> s.isOverdue(now)).orElse(null),
                                request.getSubmittedAt(),
                                request.getFinishedAt(),
                                request.isResultReceived());
                    }
                } while (page.hasNext());
            }
            return workbook.toBytes();
        }
    }

    /**
     * Totals for requests submitted in the period. Staff see their own department; admins see everything or
     * one department.
     */
    @Transactional(readOnly = true)
    public RequestStatistics statistics(CurrentUser user, LocalDate from, LocalDate to, String departmentCode,
            Long templateId) {
        String department = user.hasRole(Role.ADMIN) ? departmentCode : user.departmentCode();
        if (!user.hasRole(Role.ADMIN) && department == null) {
            return new RequestStatistics(List.of(), List.of());
        }
        Timestamp start = from == null ? null : Timestamp.from(from.atStartOfDay(zone).toInstant());
        Timestamp end = to == null ? null : Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
        Timestamp now = Timestamp.from(clock.instant());

        List<RequestStatistics.ByTemplate> byTemplate = jdbc.sql("""
                        select r.template_id,
                               (array_agg(r.title order by r.submitted_at desc))[1] as title,
                               count(*) as total,
                               count(*) filter (where r.status = 'IN_PROGRESS') as in_progress,
                               count(*) filter (where r.status = 'COMPLETED') as completed,
                               count(*) filter (where r.status = 'REJECTED') as rejected,
                               count(*) filter (where r.status = 'CANCELLED') as cancelled,
                               count(*) filter (where exists (select 1 from request_step s where s.request_id = r.id
                                                and s.status = 'IN_PROGRESS' and s.due_at < :now)) as overdue,
                               avg(extract(epoch from r.finished_at - r.submitted_at) / 86400)
                                   filter (where r.status = 'COMPLETED') as average_days
                        from service_request r
                        where (cast(:start as timestamptz) is null or r.submitted_at >= :start)
                          and (cast(:end as timestamptz) is null or r.submitted_at < :end)
                          and (cast(:template as bigint) is null or r.template_id = :template)
                          and (cast(:department as text) is null or exists (
                                select 1 from request_step s where s.request_id = r.id and s.department_code = :department))
                        group by r.template_id
                        order by total desc, title
                        """)
                .param("start", start).param("end", end).param("now", now)
                .param("template", templateId).param("department", department)
                .query((row, n) -> new RequestStatistics.ByTemplate(row.getLong("template_id"), row.getString("title"),
                        row.getLong("total"), row.getLong("in_progress"), row.getLong("completed"),
                        row.getLong("rejected"), row.getLong("cancelled"), row.getLong("overdue"),
                        nullableDouble(row.getObject("average_days"))))
                .list();

        List<RequestStatistics.ByDepartment> byDepartment = jdbc.sql("""
                        select s.department_code,
                               count(*) filter (where s.status = 'IN_PROGRESS') as in_progress,
                               count(*) filter (where s.status = 'IN_PROGRESS' and s.due_at < :now) as overdue,
                               count(*) filter (where s.status = 'COMPLETED') as completed,
                               count(*) filter (where s.status = 'REJECTED') as rejected,
                               avg(case when s.finished_at <= s.due_at then 1.0 else 0.0 end)
                                   filter (where s.status = 'COMPLETED') as on_time_rate,
                               avg(extract(epoch from s.finished_at - s.started_at) / 86400)
                                   filter (where s.status = 'COMPLETED') as average_days
                        from request_step s
                        join service_request r on r.id = s.request_id
                        where (cast(:start as timestamptz) is null or r.submitted_at >= :start)
                          and (cast(:end as timestamptz) is null or r.submitted_at < :end)
                          and (cast(:template as bigint) is null or r.template_id = :template)
                          and (cast(:department as text) is null or s.department_code = :department)
                          and s.status <> 'PENDING'
                        group by s.department_code
                        order by s.department_code
                        """)
                .param("start", start).param("end", end).param("now", now)
                .param("template", templateId).param("department", department)
                .query((row, n) -> new RequestStatistics.ByDepartment(row.getString("department_code"),
                        row.getLong("in_progress"), row.getLong("overdue"), row.getLong("completed"),
                        row.getLong("rejected"), nullableDouble(row.getObject("on_time_rate")),
                        nullableDouble(row.getObject("average_days"))))
                .list();

        return new RequestStatistics(byTemplate, byDepartment);
    }

    @Transactional(readOnly = true)
    public byte[] exportStatistics(CurrentUser user, LocalDate from, LocalDate to, String departmentCode,
            Long templateId) {
        RequestStatistics statistics = statistics(user, from, to, departmentCode, templateId);
        try (ExcelWorkbook workbook = new ExcelWorkbook(zone)) {
            workbook.sheet("By template",
                    new String[] {"Template", "Total", "In progress", "Completed", "Rejected", "Cancelled",
                            "Overdue now", "Avg days to complete"},
                    new int[] {36, 8, 11, 11, 10, 10, 12, 20});
            for (var row : statistics.byTemplate()) {
                workbook.row(row.title(), row.total(), row.inProgress(), row.completed(), row.rejected(),
                        row.cancelled(), row.overdue(), row.averageDaysToComplete());
            }
            workbook.sheet("By department",
                    new String[] {"Department", "In progress", "Overdue now", "Completed", "Rejected",
                            "On time (%)", "Avg days per step"},
                    new int[] {12, 11, 12, 11, 10, 12, 18});
            for (var row : statistics.byDepartment()) {
                workbook.row(row.departmentCode(), row.inProgress(), row.overdue(), row.completed(), row.rejected(),
                        row.onTimeRate() == null ? null : row.onTimeRate() * 100, row.averageDaysPerStep());
            }
            return workbook.toBytes();
        }
    }

    private static Double nullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
