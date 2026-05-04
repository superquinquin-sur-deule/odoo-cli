package org.hoohoot.odoo.model;

public record Cooperator(
        int id,
        String nom,
        String prenom,
        String email,
        String address,
        double parts,
        long capital
) {}
