package com.skillmap.api.presentation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Texto sin caracteres de control (ver TextRules del dominio). null es válido: la obligatoriedad la marca otra
 * anotación. Con allowLineBreaks se aceptan \n, \r y \t.
 */
@Documented
@Constraint(validatedBy = NoControlCharsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE,
        ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoControlChars {

    String message() default "El texto tiene caracteres no permitidos.";

    boolean allowLineBreaks() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
