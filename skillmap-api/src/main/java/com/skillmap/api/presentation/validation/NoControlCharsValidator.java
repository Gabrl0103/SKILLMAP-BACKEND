package com.skillmap.api.presentation.validation;

import com.skillmap.api.domain.service.TextRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoControlCharsValidator implements ConstraintValidator<NoControlChars, String> {

    private boolean allowLineBreaks;

    @Override
    public void initialize(NoControlChars annotation) {
        this.allowLineBreaks = annotation.allowLineBreaks();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return !TextRules.hasControlChars(value, allowLineBreaks);
    }
}
