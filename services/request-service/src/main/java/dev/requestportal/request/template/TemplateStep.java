package dev.requestportal.request.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "template_step")
public class TemplateStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private RequestTemplate template;

    @Column(nullable = false)
    private int position;

    @Column(name = "department_code", nullable = false, length = 16)
    private String departmentCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "sla_days", nullable = false)
    private int slaDays;

    @Column(name = "requires_attachment", nullable = false)
    private boolean requiresAttachment;

    protected TemplateStep() {
    }

    TemplateStep(RequestTemplate template, int position, TemplateRequest.StepDefinition definition) {
        this.template = template;
        this.position = position;
        this.departmentCode = definition.departmentCode();
        this.description = definition.description().strip();
        this.slaDays = definition.slaDays();
        this.requiresAttachment = definition.requiresAttachment();
    }

    public int getPosition() {
        return position;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public String getDescription() {
        return description;
    }

    public int getSlaDays() {
        return slaDays;
    }

    public boolean isRequiresAttachment() {
        return requiresAttachment;
    }
}
