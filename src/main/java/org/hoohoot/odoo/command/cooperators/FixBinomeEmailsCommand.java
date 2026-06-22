package org.hoohoot.odoo.command.cooperators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "fix-binome-emails",
        description = "Rattrape l'email manquant sur les contacts binômes (is_associated_people) en le recopiant "
                + "depuis l'homonyme (même name, hors binôme) qui possède un email. Ne corrige que si un seul email "
                + "candidat existe ; liste les cas sans source ou ambigus.",
        mixinStandardHelpOptions = true
)
public class FixBinomeEmailsCommand implements Callable<Integer> {

    @Option(
            names = "--dry-run",
            description = "Simule l'exécution sans écrire dans Odoo"
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        String prefix = dryRun ? "[dry-run] " : "";

        // 1. Contacts binômes sans email
        JsonNode binomes = odoo.searchRead("res.partner",
                List.of(List.of("is_associated_people", "=", true), List.of("email", "=", false)),
                List.of("id", "name"));

        if (binomes == null || !binomes.isArray() || binomes.isEmpty()) {
            System.err.println("Aucun contact binôme sans email : rien à faire");
            return 0;
        }

        // 2. Noms distincts à résoudre
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode b : binomes) {
            names.add(b.path("name").asText(""));
        }

        // 3. Sources d'email : homonymes hors binôme possédant un email
        JsonNode sources = odoo.searchRead("res.partner",
                List.of(
                        List.of("is_associated_people", "=", false),
                        List.of("email", "!=", false),
                        List.of("name", "in", List.copyOf(names))),
                List.of("id", "name", "email"));

        // name -> (email distinct -> id source représentatif)
        Map<String, Map<String, Integer>> emailsByName = new LinkedHashMap<>();
        if (sources != null && sources.isArray()) {
            for (JsonNode s : sources) {
                String name = s.path("name").asText("");
                String email = s.path("email").asText("").trim();
                if (email.isBlank()) {
                    continue;
                }
                emailsByName.computeIfAbsent(name, k -> new LinkedHashMap<>())
                        .putIfAbsent(email, s.path("id").asInt());
            }
        }

        // 4. Résolution + 5/6. application
        int fixed = 0;
        int noSource = 0;
        int ambiguous = 0;
        for (JsonNode b : binomes) {
            int childId = b.path("id").asInt();
            String name = b.path("name").asText("");
            Map<String, Integer> candidates = emailsByName.get(name);

            if (candidates == null || candidates.isEmpty()) {
                System.err.printf("ignoré (aucune source email) : %s (contact %d)%n", name, childId);
                noSource++;
                continue;
            }
            if (candidates.size() > 1) {
                System.err.printf("ignoré (homonymes ambigus, %d emails) : %s (contact %d)%n",
                        candidates.size(), name, childId);
                ambiguous++;
                continue;
            }

            Map.Entry<String, Integer> source = candidates.entrySet().iterator().next();
            String email = source.getKey();
            int sourceId = source.getValue();

            System.out.printf("%scontact %d : %s ← %s (source %d)%n", prefix, childId, name, email, sourceId);

            if (!dryRun) {
                odoo.executeKw("res.partner", "write",
                        List.of(List.of(childId), Map.of("email", email)));
            }
            fixed++;
        }

        // 7. Récapitulatif
        if (dryRun) {
            System.err.printf("Mode dry-run : %d email(s) à rattraper, %d sans source, %d ambigu(s) — aucune écriture%n",
                    fixed, noSource, ambiguous);
        } else {
            System.err.printf("%d email(s) rattrapé(s), %d sans source, %d ambigu(s)%n",
                    fixed, noSource, ambiguous);
        }
        return 0;
    }
}
