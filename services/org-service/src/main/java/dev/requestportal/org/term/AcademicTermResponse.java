package dev.requestportal.org.term;

import java.time.LocalDate;

public record AcademicTermResponse(
        Long id, String code, String schoolYear, int termNumber, String name, LocalDate startsOn, LocalDate endsOn) {

    static AcademicTermResponse from(AcademicTerm term) {
        return new AcademicTermResponse(
                term.getId(),
                term.getCode(),
                term.getSchoolYear(),
                term.getTermNumber(),
                term.getName(),
                term.getStartsOn(),
                term.getEndsOn());
    }
}
