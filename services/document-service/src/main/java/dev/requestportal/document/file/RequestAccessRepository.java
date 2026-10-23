package dev.requestportal.document.file;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestAccessRepository extends JpaRepository<RequestAccess, Long> {
}
