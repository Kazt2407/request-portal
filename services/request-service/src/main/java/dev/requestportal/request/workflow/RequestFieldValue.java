package dev.requestportal.request.workflow;

import dev.requestportal.request.template.FieldType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A submitted value, with the field's label and type copied so later template edits do not change it. */
@Entity
@Table(name = "request_field_value")
public class RequestFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Column(nullable = false)
    private int position;

    @Column(name = "field_key", nullable = false, length = 50)
    private String key;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private FieldType type;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> value;

    protected RequestFieldValue() {
    }

    RequestFieldValue(ServiceRequest request, SubmittedValue submitted) {
        this.request = request;
        this.position = submitted.field().getPosition();
        this.key = submitted.field().getKey();
        this.label = submitted.field().getLabel();
        this.type = submitted.field().getType();
        this.value = List.copyOf(submitted.value());
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public FieldType getType() {
        return type;
    }

    public List<String> getValue() {
        return List.copyOf(value);
    }
}
