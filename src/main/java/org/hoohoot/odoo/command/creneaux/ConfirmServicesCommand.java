package org.hoohoot.odoo.command.creneaux;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "confirm-services",
        description = "Confirme les services (shift.shift) en état brouillon entre deux dates",
        mixinStandardHelpOptions = true
)
public class ConfirmServicesCommand implements Callable<Integer> {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Option(
            names = "--begin-date",
            paramLabel = "DATE",
            required = true,
            description = "Date de début (jj/MM/aaaa)"
    )
    String beginDate;

    @Option(
            names = "--end-date",
            paramLabel = "DATE",
            required = true,
            description = "Date de fin (jj/MM/aaaa)"
    )
    String endDate;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        LocalDate begin;
        LocalDate end;
        
        try {
            begin = LocalDate.parse(beginDate, INPUT_DATE_FORMAT);
        } catch (Exception e) {
            System.err.println(beginDate + " : mauvais format de date (jj/MM/aaaa) pour --begin-date");
            return 2;
        }
        
        try {
            end = LocalDate.parse(endDate, INPUT_DATE_FORMAT);
        } catch (Exception e) {
            System.err.println(endDate + " : mauvais format de date (jj/MM/aaaa) pour --end-date");
            return 2;
        }
        
        if (begin.isAfter(end)) {
            System.err.println("--begin-date doit être antérieure ou égale à --end-date");
            return 2;
        }

        List<Object> domain = List.of(
                List.of("state", "=", "draft"),
                List.of("date_begin", ">=", begin.format(ODOO_DATE_FORMAT) + " 00:00:00"),
                List.of("date_begin", "<=", end.format(ODOO_DATE_FORMAT) + " 23:59:59")
        );
        JsonNode shifts = odoo.searchRead("shift.shift", domain, List.of("id", "name"));

        if (shifts == null || !shifts.isArray() || shifts.isEmpty()) {
            System.err.printf("0 service(s) en brouillon entre %s et %s%n", beginDate, endDate);
            return 0;
        }

        List<Integer> ids = new ArrayList<>();
        shifts.forEach(s -> ids.add(s.get("id").asInt()));

        odoo.executeKw("shift.shift", "button_confirm", List.of(ids));

        System.err.printf("%d service(s) confirmé(s) entre %s et %s%n",
                ids.size(), beginDate, endDate);
        return 0;
    }
}
