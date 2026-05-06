package org.hoohoot.odoo.command.cooperators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.format.CsvFormatter;
import org.hoohoot.odoo.format.PrettyFormatter;
import org.hoohoot.odoo.model.Cooperator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "list",
        description = "Liste les coopérateurs",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    public enum OutputFormat { pretty, csv }

    private static final DateTimeFormatter INPUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Option(
            names = "--at-date",
            paramLabel = "DATE",
            description = "Filtre: ne compte que les parts dont date_invoice <= DATE (jj/MM/aaaa)"
    )
    String atDate;

    @Option(
            names = "--output",
            paramLabel = "FORMAT",
            description = "Format de sortie: ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "pretty"
    )
    OutputFormat output;

    @Option(
            names = "--duplicate-email",
            description = "N'afficher que les coopérateurs qui partagent leur email avec au moins un autre"
    )
    boolean duplicateEmail;

    @Option(
            names = "--no-email",
            description = "N'afficher que les coopérateurs sans email"
    )
    boolean noEmail;

    @Inject
    OdooClient odoo;

    @Inject
    PrettyFormatter pretty;

    @Inject
    CsvFormatter csv;

    @Override
    public Integer call() {
        LocalDate parsedAtDate = null;
        if (atDate != null && !atDate.isBlank()) {
            try {
                parsedAtDate = LocalDate.parse(atDate, INPUT_FMT);
            } catch (Exception e) {
                System.err.println(atDate + " : mauvais format de date (jj/MM/aaaa)");
                return 2;
            }
        }

        List<Cooperator> coops = fetchCooperators(parsedAtDate);
        if (noEmail) {
            coops.removeIf(c -> !normalizeEmail(c.email()).isEmpty());
        }
        if (duplicateEmail) {
            Map<String, Long> counts = new HashMap<>();
            for (Cooperator c : coops) {
                String key = normalizeEmail(c.email());
                if (!key.isEmpty()) {
                    counts.merge(key, 1L, Long::sum);
                }
            }
            coops.removeIf(c -> {
                String key = normalizeEmail(c.email());
                return key.isEmpty() || counts.getOrDefault(key, 0L) < 2;
            });
        }
        if (duplicateEmail) {
            coops.sort((a, b) -> {
                int c = normalizeEmail(a.email()).compareTo(normalizeEmail(b.email()));
                if (c != 0) return c;
                c = a.nom().compareToIgnoreCase(b.nom());
                return c != 0 ? c : a.prenom().compareToIgnoreCase(b.prenom());
            });
        } else {
            coops.sort((a, b) -> {
                int c = a.nom().compareToIgnoreCase(b.nom());
                return c != 0 ? c : a.prenom().compareToIgnoreCase(b.prenom());
            });
        }

        switch (output) {
            case csv -> printCsv(coops);
            case pretty -> printPretty(coops);
        }
        System.err.printf("%d coopérateur(s)%s%n",
                coops.size(),
                parsedAtDate != null ? " au " + atDate : "");
        return 0;
    }

    private List<Cooperator> fetchCooperators(LocalDate parsedAtDate) {
        List<Object> partnerDomain = List.of(
                List.of("is_member", "=", true),
                List.of("active", "in", List.of(true, false))
        );
        List<String> partnerFields = List.of(
                "id", "name", "email", "street", "zip", "city", "total_partner_owned_share"
        );
        JsonNode partners = odoo.searchRead("res.partner", partnerDomain, partnerFields);

        if (partners == null || !partners.isArray() || partners.isEmpty()) {
            return List.of();
        }

        List<Integer> partnerIds = new ArrayList<>();
        partners.forEach(p -> partnerIds.add(p.get("id").asInt()));

        List<Object> invoiceDomain = new ArrayList<>();
        invoiceDomain.add(List.of("partner_id", "in", partnerIds));
        invoiceDomain.add(List.of("is_capital_fundraising", "=", true));
        invoiceDomain.add(List.of("state", "=", "paid"));
        invoiceDomain.add(List.of("date_invoice", "!=", false));
        if (parsedAtDate != null) {
            invoiceDomain.add(List.of("date_invoice", "<=", parsedAtDate.format(ODOO_FMT)));
        }
        JsonNode invoices = odoo.searchRead(
                "account.invoice",
                invoiceDomain,
                List.of("partner_id", "amount_total_signed", "date_invoice")
        );

        Map<Integer, Double> capitalByPartner = new HashMap<>();
        if (invoices != null && invoices.isArray()) {
            for (JsonNode inv : invoices) {
                JsonNode partnerRef = inv.get("partner_id");
                if (partnerRef == null || !partnerRef.isArray() || partnerRef.isEmpty()) {
                    continue;
                }
                int pid = partnerRef.get(0).asInt();
                double amount = inv.get("amount_total_signed").asDouble(0);
                capitalByPartner.merge(pid, amount, Double::sum);
            }
        }

        List<Cooperator> result = new ArrayList<>();
        for (JsonNode p : partners) {
            int id = p.get("id").asInt();
            double capital = capitalByPartner.getOrDefault(id, 0.0);
            if (capital == 0.0) {
                continue;
            }
            String fullName = textOrEmpty(p, "name");
            String nom;
            String prenom;
            int comma = fullName.indexOf(',');
            if (comma >= 0) {
                nom = fullName.substring(0, comma).trim();
                prenom = fullName.substring(comma + 1).trim();
            } else {
                nom = fullName;
                prenom = "";
            }
            String address = String.format("%s %s %s",
                    textOrEmpty(p, "street"),
                    textOrEmpty(p, "zip"),
                    textOrEmpty(p, "city")
            ).replaceAll("\\s+", " ").trim();
            result.add(new Cooperator(
                    id,
                    nom,
                    prenom,
                    textOrEmpty(p, "email"),
                    address,
                    p.path("total_partner_owned_share").asDouble(0),
                    (long) capital
            ));
        }
        return result;
    }

    private void printCsv(List<Cooperator> coops) {
        csv.print(
                new String[]{"Id", "Nom", "Prenom", "Email", "Adresse", "Nb de parts", "Capital"},
                toRows(coops)
        );
    }

    private void printPretty(List<Cooperator> coops) {
        pretty.print(
                new String[]{"Id", "Nom", "Prénom", "Email", "Adresse", "Parts", "Capital"},
                toRows(coops),
                new boolean[]{true, false, false, false, false, true, true}
        );
    }

    private static String[][] toRows(List<Cooperator> coops) {
        String[][] rows = new String[coops.size()][];
        for (int i = 0; i < coops.size(); i++) {
            Cooperator c = coops.get(i);
            rows[i] = new String[]{
                    String.valueOf(c.id()),
                    c.nom(),
                    c.prenom(),
                    c.email(),
                    c.address(),
                    formatParts(c.parts()),
                    String.valueOf(c.capital())
            };
        }
        return rows;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isBoolean()) {
            return "";
        }
        return v.asText("");
    }

    private static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase();
    }

    private static String formatParts(double parts) {
        if (parts == Math.floor(parts)) {
            return String.valueOf((long) parts);
        }
        return String.valueOf(parts);
    }
}
