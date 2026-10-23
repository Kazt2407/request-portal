package dev.requestportal.request.report;

import java.util.List;

/** Totals for a period, by template and by department. */
public record RequestStatistics(List<ByTemplate> byTemplate, List<ByDepartment> byDepartment) {

    /** @param averageDaysToComplete from submission to completion, for completed requests */
    public record ByTemplate(long templateId, String title, long total, long inProgress, long completed,
            long rejected, long cancelled, long overdue, Double averageDaysToComplete) {
    }

    /**
     * @param onTimeRate          share of completed steps finished by their due date, 0 to 1
     * @param averageDaysPerStep  from a step starting to it being completed
     */
    public record ByDepartment(String departmentCode, long inProgress, long overdue, long completed, long rejected,
            Double onTimeRate, Double averageDaysPerStep) {
    }
}
