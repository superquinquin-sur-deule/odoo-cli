package org.hoohoot.odoo.model;

import java.time.LocalDate;

public record Cooperator(
        int id,
        String nom,
        String prenom,
        String email,
        String address,
        double parts,
        long capital,
        LocalDate inscriptionDate,
        String status,
        boolean isBinome
) {}
