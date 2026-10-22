package dev.requestportal.web;

/** The request is well-formed but breaks a business rule, e.g. a staff member without a department. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
