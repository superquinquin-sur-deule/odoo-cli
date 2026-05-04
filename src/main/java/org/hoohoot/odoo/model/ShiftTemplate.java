package org.hoohoot.odoo.model;

public record ShiftTemplate(
        int id,
        String name,
        String weekName,
        String shiftType,
        String startDatetime,
        String endDatetime,
        double duration,
        int seatsMin,
        int seatsMax,
        int seatsReserved
) {}
