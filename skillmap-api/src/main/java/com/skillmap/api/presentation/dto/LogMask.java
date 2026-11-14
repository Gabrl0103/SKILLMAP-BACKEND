package com.skillmap.api.presentation.dto;

/** Para los toString de los DTO: un correo en un log queda como "a***@dominio.com". */
final class LogMask {

    private LogMask() {
    }

    static String email(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
