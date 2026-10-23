package dev.requestportal.document.form;

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

/** A value the user types when downloading the form. */
@Entity
@Table(name = "form_field")
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id")
    private FormTemplate form;

    @Column(nullable = false)
    private int position;

    @Column(name = "field_key", nullable = false, length = 50)
    private String key;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 200)
    private String hint;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private FormFieldType type;

    @Column(nullable = false)
    private boolean required;

    protected FormField() {
    }

    FormField(FormTemplate form, int position, FormViews.FieldDefinition definition) {
        this.form = form;
        this.position = position;
        this.key = definition.key();
        this.label = definition.label().strip();
        this.hint = definition.hint();
        this.type = definition.type();
        this.required = definition.required();
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getHint() {
        return hint;
    }

    public FormFieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }
}
