package org.hoohoot.odoo.command.cooperators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.BrevoClient;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "sync-brevo",
        description = "Synchronise les nouveaux coopérateurs (créés depuis --since) vers une liste de contacts Brevo (attributs NOM/PRENOM + email)",
        mixinStandardHelpOptions = true
)
public class SyncBrevoCommand implements Callable<Integer> {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Option(
            names = "--since",
            paramLabel = "DATE",
            required = true,
            description = "Ne traite que les coopérateurs dont create_date >= DATE 00:00:00 (jj/MM/aaaa)"
    )
    String since;

    @Option(
            names = "--brevo-list-id",
            paramLabel = "ID",
            required = true,
            description = "Id de la liste de contacts dans Brevo"
    )
    int brevoListId;

    @Option(
            names = "--dry-run",
            description = "Log seulement les coopérateurs qui auraient été ajoutés, sans appeler Brevo"
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    @Inject
    BrevoClient brevo;

    @Override
    public Integer call() {
        LocalDate sinceDate;
        try {
            sinceDate = LocalDate.parse(since, INPUT_DATE_FORMAT);
        } catch (Exception e) {
            System.err.println(since + " : mauvais format de date (jj/MM/aaaa) pour --since");
            return 2;
        }

        List<Object> domain = List.of(
                List.of("is_member", "=", true),
                List.of("email", "!=", false),
                List.of("create_date", ">=", sinceDate.format(ODOO_DATE_FORMAT) + " 00:00:00")
        );
        JsonNode members = odoo.searchRead("res.partner", domain,
                List.of("id", "name", "email", "create_date"));

        int added = 0;
        int skipped = 0;
        int errors = 0;

        if (members != null && members.isArray()) {
            for (JsonNode member : members) {
                String name = member.path("name").asText("");
                String email = member.path("email").asText("").trim();
                if (email.isBlank()) {
                    continue;
                }

                // name au format Odoo "NOM, Prénom" ; sans virgule tout va dans NOM
                String[] parts = name.split(", ", 2);
                String nom = parts[0];
                String prenom = parts.length > 1 ? parts[1] : "";

                if (dryRun) {
                    System.out.printf("[dry-run] %s <%s>%n", name, email);
                    added++;
                    continue;
                }

                BrevoClient.ContactResult result = brevo.createContact(email, nom, prenom, brevoListId);
                switch (result.status()) {
                    case CREATED -> {
                        System.out.printf("%s <%s> : ajouté à la liste Brevo %d%n", name, email, brevoListId);
                        added++;
                    }
                    case DUPLICATE -> {
                        System.out.printf("%s <%s> : déjà présent dans Brevo, ignoré%n", name, email);
                        skipped++;
                    }
                    case ERROR -> {
                        System.err.printf("%s <%s> : erreur Brevo — %s%n", name, email, result.message());
                        errors++;
                    }
                }
            }
        }

        if (dryRun) {
            System.err.printf("Mode dry-run : %d contact(s) auraient été ajoutés à la liste Brevo %d%n",
                    added, brevoListId);
        } else {
            System.err.printf("%d contact(s) ajouté(s), %d ignoré(s) (doublons), %d erreur(s)%n",
                    added, skipped, errors);
        }
        return errors > 0 ? 1 : 0;
    }
}
