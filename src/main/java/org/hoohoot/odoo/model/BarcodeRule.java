package org.hoohoot.odoo.model;

public record BarcodeRule(
        int id,
        String name,
        String type,
        String encoding,
        String pattern,
        String createDate,
        int sequence,
        String transformExpr
) {}
