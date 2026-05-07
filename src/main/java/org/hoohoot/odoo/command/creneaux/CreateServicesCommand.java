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
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "create-services",
        description = "Crée les services (shift.shift) à partir des shift.template actifs entre deux dates",
        mixinStandardHelpOptions = true
)
public class CreateServicesCommand implements Callable<Integer> {

    private static final DateTimeFormatter INPUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

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
            begin = LocalDate.parse(beginDate, INPUT_FMT);
        } catch (Exception e) {
            System.err.println(beginDate + " : mauvais format de date (jj/MM/aaaa) pour --begin-date");
            return 2;
        }
        try {
            end = LocalDate.parse(endDate, INPUT_FMT);
        } catch (Exception e) {
            System.err.println(endDate + " : mauvais format de date (jj/MM/aaaa) pour --end-date");
            return 2;
        }
        if (begin.isAfter(end)) {
            System.err.println("--begin-date doit être antérieure ou égale à --end-date");
            return 2;
        }

        JsonNode templates = odoo.searchRead(
                "shift.template",
                List.of(),
                List.of("id", "name")
        );
        if (templates == null || !templates.isArray() || templates.isEmpty()) {
            System.err.println("Aucun créneau actif trouvé");
            return 0;
        }

        List<Integer> ids = new ArrayList<>();
        templates.forEach(t -> ids.add(t.get("id").asInt()));

        Map<String, Object> wizardVals = Map.of(
                "template_ids", List.of(List.of(6, 0, ids)),
                "date_from", begin.format(ODOO_FMT),
                "date_to", end.format(ODOO_FMT)
        );
        JsonNode wizardId = odoo.executeKw(
                "create.shifts.wizard",
                "create",
                List.of(wizardVals)
        );
        if (wizardId == null || !wizardId.isInt()) {
            System.err.println("Impossible de créer le wizard create.shifts.wizard");
            return 1;
        }

        odoo.executeKw(
                "create.shifts.wizard",
                "create_shifts",
                List.of(List.of(wizardId.asInt()))
        );

        System.err.printf("%d créneau(x) traité(s) entre %s et %s%n",
                ids.size(), beginDate, endDate);
        return 0;
    }
}
