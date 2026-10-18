package com.skillmap.api.presentation.dto;

/** DELETE /api/auth/me: la contraseña confirma el borrado. toString no la muestra. */
public record DeleteAccountRequest(String password) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[password=***]";
    }
}
