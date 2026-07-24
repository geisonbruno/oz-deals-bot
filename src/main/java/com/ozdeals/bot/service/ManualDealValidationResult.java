package com.ozdeals.bot.service;

import java.util.List;

public record ManualDealValidationResult(List<String> errors) {

    public boolean isValid() {
        return errors.isEmpty();
    }
}
