package dev.requestportal.request.template;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTemplateRepository extends JpaRepository<RequestTemplate, Long> {

    List<RequestTemplate> findAllByOrderByTitleAsc();

    List<RequestTemplate> findByStatusOrderByTitleAsc(TemplateStatus status);

    List<RequestTemplate> findByStatusAndAudienceInOrderByTitleAsc(TemplateStatus status, Collection<Audience> audiences);
}
