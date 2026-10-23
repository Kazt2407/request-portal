package dev.requestportal.document.form;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {

    List<FormTemplate> findAllByOrderByTitleAsc();

    List<FormTemplate> findByActiveTrueAndAudienceInOrderByTitleAsc(Collection<FormAudience> audiences);
}
