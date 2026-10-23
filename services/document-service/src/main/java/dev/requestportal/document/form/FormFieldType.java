package dev.requestportal.document.form;

public enum FormFieldType {
    TEXT,
    /** A whole amount; the form can also show it in words as ${key_words}. */
    NUMBER,
    /** ISO date (2026-10-22), shown as 22/10/2026. */
    DATE
}
