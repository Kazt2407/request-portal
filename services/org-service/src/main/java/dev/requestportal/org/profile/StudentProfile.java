package dev.requestportal.org.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_profile")
public class StudentProfile {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "student_code", nullable = false, length = 20)
    private String studentCode;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "class_name", length = 50)
    private String className;

    @Column(length = 200)
    private String faculty;

    @Column(length = 200)
    private String major;

    @Column(length = 20)
    private String phone;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StudentProfile() {
    }

    StudentProfile(UUID accountId) {
        this.accountId = accountId;
    }

    void update(ProfileViews.ProfileUpdate update) {
        studentCode = update.studentCode().strip().toUpperCase();
        dateOfBirth = update.dateOfBirth();
        className = update.className();
        faculty = update.faculty();
        major = update.major();
        phone = update.phone();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getClassName() {
        return className;
    }

    public String getFaculty() {
        return faculty;
    }

    public String getMajor() {
        return major;
    }

    public String getPhone() {
        return phone;
    }

    public Long getVersion() {
        return version;
    }
}
