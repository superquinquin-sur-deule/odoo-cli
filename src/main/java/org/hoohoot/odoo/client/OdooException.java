package org.hoohoot.odoo.client;

public class OdooException extends RuntimeException {
    public OdooException(String message) {
        super(message);
    }

    public OdooException(String message, Throwable cause) {
        super(message, cause);
    }
}
