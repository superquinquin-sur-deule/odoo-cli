package org.hoohoot.odoo.command.articles;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "apply-supplier-coefficient",
        description = "Applique un coefficient (ex. surtaxe carburant) à tous les produits d'un fournisseur en l'affectant à un slot de coefficient libre (coeffN_id). Le coefficient product.coefficient est retrouvé par son nom ou créé. Idempotent : un produit qui porte déjà ce coefficient est sauté.",
        mixinStandardHelpOptions = true
)
public class ApplySupplierCoefficientCommand implements Callable<Integer> {

    enum OperationType {multiplier, fixed}

    private static final int FREE_SLOT_MIN = 2;
    private static final int FREE_SLOT_MAX = 6;

    @Option(
            names = "--supplier-id",
            paramLabel = "ID",
            description = "Id du fournisseur (res.partner). Exclusif avec --supplier-name."
    )
    Integer supplierId;

    @Option(
            names = "--supplier-name",
            paramLabel = "NOM",
            description = "Nom exact du fournisseur (résolu parmi les res.partner supplier=true). Exclusif avec --supplier-id."
    )
    String supplierName;

    @Option(
            names = "--name",
            paramLabel = "NOM",
            description = "Nom du product.coefficient à appliquer (ex. « Surtaxe carburant 2026 »)."
    )
    String name;

    @Option(
            names = "--value",
            paramLabel = "VALEUR",
            description = "Valeur du coefficient (ex. 0.02 = +2 %)."
    )
    Double value;

    @Option(
            names = "--operation-type",
            paramLabel = "TYPE",
            description = "Type d'opération du coefficient : ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE}).",
            defaultValue = "multiplier"
    )
    OperationType operationType;

    @Option(
            names = "--slot",
            paramLabel = "N",
            description = "Slot de coefficient à utiliser (coeffN_id, 1-9). Si omis : premier slot libre parmi coeff2..coeff6."
    )
    Integer slot;

    @Option(
            names = "--dry-run",
            description = "Simule l'exécution sans écrire dans Odoo."
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        if ((supplierId == null) == (supplierName == null)) {
            System.err.println("Préciser exactement un de --supplier-id ou --supplier-name");
            return 2;
        }
        if (name == null || name.isBlank()) {
            System.err.println("--name est requis (nom du product.coefficient)");
            return 2;
        }
        if (value == null) {
            System.err.println("--value est requis (valeur du coefficient, ex. 0.02)");
            return 2;
        }
        if (slot != null && (slot < 1 || slot > 9)) {
            System.err.println("--slot doit être compris entre 1 et 9");
            return 2;
        }

        String prefix = dryRun ? "[dry-run] " : "";

        // 1) Résoudre le fournisseur
        int partnerId;
        String supplierLabel;
        if (supplierName != null) {
            JsonNode partners = odoo.searchRead("res.partner",
                    List.of(List.of("supplier", "=", true), List.of("name", "=", supplierName)),
                    List.of("id", "name"));
            if (partners == null || !partners.isArray() || partners.isEmpty()) {
                System.err.println("Aucun fournisseur trouvé pour le nom « " + supplierName + " »");
                return 2;
            }
            if (partners.size() > 1) {
                System.err.println(partners.size() + " fournisseurs correspondent au nom « " + supplierName
                        + " » ; préciser --supplier-id");
                return 2;
            }
            partnerId = partners.get(0).get("id").asInt();
            supplierLabel = partners.get(0).path("name").asText();
        } else {
            partnerId = supplierId;
            supplierLabel = "id " + supplierId;
        }
        System.out.println(prefix + "Fournisseur : " + supplierLabel);

        // 2) Trouver ou créer le coefficient
        int coefId = resolveCoefficient(prefix);

        // 3) Lister les produits du fournisseur via product.supplierinfo
        JsonNode sellers = odoo.searchRead("product.supplierinfo",
                List.of(List.of("name", "=", partnerId)),
                List.of("product_tmpl_id"));
        Set<Integer> tmplIds = new LinkedHashSet<>();
        if (sellers != null && sellers.isArray()) {
            for (JsonNode s : sellers) {
                JsonNode tref = s.get("product_tmpl_id");
                if (tref != null && tref.isArray() && !tref.isEmpty()) {
                    tmplIds.add(tref.get(0).asInt());
                }
            }
        }
        if (tmplIds.isEmpty()) {
            System.err.println("Aucun produit trouvé pour ce fournisseur");
            return 0;
        }

        // 4) Lire les slots de coefficient des produits
        List<String> fields = new ArrayList<>(List.of("name", "list_price"));
        for (int n = 1; n <= 9; n++) {
            fields.add("coeff" + n + "_id");
        }
        JsonNode tmpls = odoo.searchRead("product.template",
                List.of(List.of("id", "in", new ArrayList<>(tmplIds))), fields);

        // 5) Appliquer
        int modified = 0;
        int skipped = 0;
        if (tmpls != null && tmpls.isArray()) {
            for (JsonNode t : tmpls) {
                int tid = t.get("id").asInt();
                String tname = t.path("name").asText("");
                double listPrice = t.path("list_price").asDouble(0);

                Integer already = slotHolding(t, coefId);
                if (already != null) {
                    System.out.printf("%s%s (id %d) : déjà appliqué (coeff%d)%n", prefix, tname, tid, already);
                    skipped++;
                    continue;
                }

                int targetSlot;
                if (slot != null) {
                    if (slotCoefId(t, slot) != null) {
                        System.out.printf("%s%s (id %d) : slot %d occupé (%s) — sauté%n",
                                prefix, tname, tid, slot, slotCoefName(t, slot));
                        skipped++;
                        continue;
                    }
                    targetSlot = slot;
                } else {
                    Integer free = firstFreeSlot(t);
                    if (free == null) {
                        System.out.printf("%s%s (id %d) : aucun slot libre (coeff%d..coeff%d) — sauté%n",
                                prefix, tname, tid, FREE_SLOT_MIN, FREE_SLOT_MAX);
                        skipped++;
                        continue;
                    }
                    targetSlot = free;
                }

                System.out.printf("%s%s (id %d) : coeff%d ← %s (list_price actuel %.2f)%n",
                        prefix, tname, tid, targetSlot, name, listPrice);
                if (!dryRun) {
                    odoo.executeKw("product.template", "write",
                            List.of(List.of(tid), Map.of("coeff" + targetSlot + "_id", coefId)));
                }
                modified++;
            }
        }

        if (dryRun) {
            System.err.printf("[dry-run] %d produit(s) seraient modifié(s), %d sauté(s) — aucune écriture%n",
                    modified, skipped);
        } else {
            System.err.printf("%d produit(s) modifié(s), %d sauté(s)%n", modified, skipped);
        }
        return 0;
    }

    /**
     * Retrouve le product.coefficient par son nom, sinon le crée (sauf en dry-run).
     * Renvoie l'id du coefficient (ou un id sentinelle 0 en dry-run sans création réelle,
     * jamais utilisé pour une écriture).
     */
    private int resolveCoefficient(String prefix) {
        JsonNode found = odoo.searchRead("product.coefficient",
                List.of(List.of("name", "=", name)),
                List.of("id", "value", "operation_type"));
        if (found != null && found.isArray() && !found.isEmpty()) {
            JsonNode c = found.get(0);
            int id = c.get("id").asInt();
            double existingValue = c.path("value").asDouble();
            String existingType = c.path("operation_type").asText("");
            System.out.printf("%sCoefficient « %s » trouvé (id %d, %s %s)%n",
                    prefix, name, id, existingType, fmt(existingValue));
            if (Math.abs(existingValue - value) > 1e-9 || !existingType.equals(operationType.name())) {
                System.err.printf(
                        "Attention : le coefficient existant (%s %s) diffère de la demande (%s %s) ; valeur existante conservée%n",
                        existingType, fmt(existingValue), operationType.name(), fmt(value));
            }
            return id;
        }

        if (dryRun) {
            System.out.printf("%sCoefficient « %s » serait créé (%s %s)%n",
                    prefix, name, operationType.name(), fmt(value));
            return 0;
        }
        JsonNode created = odoo.executeKw("product.coefficient", "create",
                List.of(Map.of(
                        "name", name,
                        "value", value,
                        "operation_type", operationType.name()
                )));
        int id = created.asInt();
        System.out.printf("Coefficient « %s » créé (id %d, %s %s)%n",
                name, id, operationType.name(), fmt(value));
        return id;
    }

    /** Id du coefficient présent dans le slot N, ou null si vide. */
    private Integer slotCoefId(JsonNode tmpl, int n) {
        JsonNode v = tmpl.get("coeff" + n + "_id");
        if (v == null || !v.isArray() || v.isEmpty()) {
            return null;
        }
        return v.get(0).asInt();
    }

    private String slotCoefName(JsonNode tmpl, int n) {
        JsonNode v = tmpl.get("coeff" + n + "_id");
        if (v == null || !v.isArray() || v.size() < 2) {
            return "";
        }
        return v.get(1).asText();
    }

    /** Premier slot (parmi coeff2..coeff6) sans coefficient, ou null si tous occupés. */
    private Integer firstFreeSlot(JsonNode tmpl) {
        for (int n = FREE_SLOT_MIN; n <= FREE_SLOT_MAX; n++) {
            if (slotCoefId(tmpl, n) == null) {
                return n;
            }
        }
        return null;
    }

    /** Numéro de slot (1..9) portant déjà coefId, ou null. */
    private Integer slotHolding(JsonNode tmpl, int coefId) {
        for (int n = 1; n <= 9; n++) {
            Integer id = slotCoefId(tmpl, n);
            if (id != null && id == coefId) {
                return n;
            }
        }
        return null;
    }

    private static String fmt(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
