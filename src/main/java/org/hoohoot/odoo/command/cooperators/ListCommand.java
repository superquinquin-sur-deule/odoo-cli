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
import java.util.Comparator;
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

    public enum GroupBy { binome }

    public enum SortBy { id, nom, prenom, email, adresse, parts, capital, inscription }

    public enum SortDirection { asc, desc }

    private static final String BINOME_PREFIX = "└─→";

    private static final DateTimeFormatter INPUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    @Option(
            names = "--group-by",
            paramLabel = "GROUP",
            description = "Grouper la sortie : ${COMPLETION-CANDIDATES}"
    )
    GroupBy groupBy;

    @Option(
            names = "--sort-by",
            paramLabel = "COLUMN",
            description = "Colonne de tri : ${COMPLETION-CANDIDATES}"
    )
    SortBy sortBy;

    @Option(
            names = "--sort-direction",
            paramLabel = "DIR",
            description = "Sens du tri : ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "asc"
    )
    SortDirection sortDirection;

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
        coops.sort(buildComparator());

        Map<Integer, List<Cooperator>> binomesByParent = Map.of();
        if (groupBy == GroupBy.binome) {
            binomesByParent = fetchBinomesByParent(coops);
        }

        switch (output) {
            case csv -> printCsv(coops, binomesByParent);
            case pretty -> printPretty(coops, binomesByParent);
        }
        System.err.printf("%d coopérateur(s)%s%n",
                coops.size(),
                parsedAtDate != null ? " au " + atDate : "");
        return 0;
    }

    private Comparator<Cooperator> buildComparator() {
        if (sortBy != null) {
            Comparator<Cooperator> cmp = switch (sortBy) {
                case id -> Comparator.comparingInt(Cooperator::id);
                case nom -> Comparator.comparing(Cooperator::nom, String.CASE_INSENSITIVE_ORDER);
                case prenom -> Comparator.comparing(Cooperator::prenom, String.CASE_INSENSITIVE_ORDER);
                case email -> Comparator.<Cooperator, String>comparing(c -> normalizeEmail(c.email()));
                case adresse -> Comparator.comparing(Cooperator::address, String.CASE_INSENSITIVE_ORDER);
                case parts -> Comparator.comparingDouble(Cooperator::parts);
                case capital -> Comparator.comparingLong(Cooperator::capital);
                case inscription -> Comparator.comparing(
                        Cooperator::inscriptionDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
            };
            return sortDirection == SortDirection.desc ? cmp.reversed() : cmp;
        }
        if (duplicateEmail) {
            return Comparator
                    .<Cooperator, String>comparing(c -> normalizeEmail(c.email()))
                    .thenComparing(Cooperator::nom, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Cooperator::prenom, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator
                .comparing(Cooperator::nom, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Cooperator::prenom, String.CASE_INSENSITIVE_ORDER);
    }

    private Map<Integer, List<Cooperator>> fetchBinomesByParent(List<Cooperator> coops) {
        if (coops.isEmpty()) {
            return Map.of();
        }
        List<Integer> parentIds = new ArrayList<>();
        for (Cooperator c : coops) {
            parentIds.add(c.id());
        }
        List<Object> domain = List.of(
                List.of("parent_id", "in", parentIds),
                List.of("is_associated_people", "=", true)
        );
        List<String> fields = List.of("id", "name", "email", "street", "zip", "city", "parent_id");
        JsonNode partners = odoo.searchRead("res.partner", domain, fields);

        Map<Integer, List<Cooperator>> result = new HashMap<>();
        if (partners == null || !partners.isArray()) {
            return result;
        }
        for (JsonNode p : partners) {
            JsonNode parentRef = p.get("parent_id");
            if (parentRef == null || !parentRef.isArray() || parentRef.isEmpty()) {
                continue;
            }
            int parentId = parentRef.get(0).asInt();
            int id = p.get("id").asInt();
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
            result.computeIfAbsent(parentId, k -> new ArrayList<>())
                    .add(new Cooperator(id, nom, prenom, textOrEmpty(p, "email"), address, 0, 0, null));
        }
        for (List<Cooperator> list : result.values()) {
            list.sort((a, b) -> {
                int c = a.nom().compareToIgnoreCase(b.nom());
                return c != 0 ? c : a.prenom().compareToIgnoreCase(b.prenom());
            });
        }
        return result;
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
        Map<Integer, LocalDate> firstInvoiceByPartner = new HashMap<>();
        if (invoices != null && invoices.isArray()) {
            for (JsonNode inv : invoices) {
                JsonNode partnerRef = inv.get("partner_id");
                if (partnerRef == null || !partnerRef.isArray() || partnerRef.isEmpty()) {
                    continue;
                }
                int pid = partnerRef.get(0).asInt();
                double amount = inv.get("amount_total_signed").asDouble(0);
                capitalByPartner.merge(pid, amount, Double::sum);

                String dateText = textOrEmpty(inv, "date_invoice");
                if (!dateText.isEmpty()) {
                    try {
                        LocalDate d = LocalDate.parse(dateText, ODOO_FMT);
                        firstInvoiceByPartner.merge(pid, d, (a, b) -> a.isBefore(b) ? a : b);
                    } catch (Exception ignore) {
                    }
                }
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
                    (long) capital,
                    firstInvoiceByPartner.get(id)
            ));
        }
        return result;
    }

    private void printCsv(List<Cooperator> coops, Map<Integer, List<Cooperator>> binomesByParent) {
        csv.print(
                new String[]{"Id", "Nom", "Prenom", "Email", "Adresse", "Nb de parts", "Capital", "Inscription"},
                toRows(coops, binomesByParent)
        );
    }

    private void printPretty(List<Cooperator> coops, Map<Integer, List<Cooperator>> binomesByParent) {
        pretty.print(
                new String[]{"Id", "Nom", "Prénom", "Email", "Adresse", "Parts", "Capital", "Inscription"},
                toRows(coops, binomesByParent),
                new boolean[]{true, false, false, false, false, true, true, false}
        );
    }

    private static String[][] toRows(List<Cooperator> coops, Map<Integer, List<Cooperator>> binomesByParent) {
        List<String[]> rows = new ArrayList<>();
        for (Cooperator c : coops) {
            rows.add(new String[]{
                    String.valueOf(c.id()),
                    c.nom(),
                    c.prenom(),
                    c.email(),
                    c.address(),
                    formatParts(c.parts()),
                    String.valueOf(c.capital()),
                    formatDate(c.inscriptionDate())
            });
            for (Cooperator b : binomesByParent.getOrDefault(c.id(), List.of())) {
                rows.add(new String[]{
                        BINOME_PREFIX,
                        b.nom(),
                        b.prenom(),
                        b.email(),
                        b.address(),
                        "",
                        "",
                        ""
                });
            }
        }
        return rows.toArray(new String[0][]);
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

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_FMT);
    }
}
