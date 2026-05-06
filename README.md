# odoo-cli

CLI Quarkus 3 (Java 21) qui interroge une instance Odoo v12 + Module FoodCoop via JSON-RPC.

## Installation

Installation rapide de la dernière version (binaire natif Linux x86_64) dans `~/.local/bin` :

```bash
curl -fsSL https://raw.githubusercontent.com/superquinquin-sur-deule/odoo-cli/main/install.sh | sh
```

Le script télécharge la dernière release GitHub, place le binaire dans `~/.local/bin/odoo-cli` et le rend exécutable.
Assurez-vous que `~/.local/bin` est dans votre `PATH` (le script vous le rappellera sinon).

## Configuration

Les credentials sont lus depuis `application.properties` ou un fichier `.env` à la racine :

| Clé                    | Description                                     |
|------------------------|-------------------------------------------------|
| `odoo.url`             | URL de l'instance (ex: `http://localhost:8069`) |
| `odoo.database`        | Nom de la base                                  |
| `odoo.login`           | Login utilisateur                               |
| `odoo.password`        | Mot de passe                                    |
| `odoo.timeout-seconds` | Timeout HTTP (défaut: 60)                       |

## Lancement

En dev : `mvn quarkus:dev -Dquarkus.args="<commande> <sous-commande> [options]"`.
En package : `java -jar target/quarkus-app/quarkus-run.jar <commande> ...`.

## Commandes

### `articles` — gérer les articles (`product.product`)

#### `articles update-internal-references`

Met à jour en masse les références internes (`default_code`) des produits depuis un fichier CSV.

| Option         | Description                                                            |
|----------------|------------------------------------------------------------------------|
| `--csv FILE`   | Fichier CSV à 2 colonnes avec en-tête `Name,InternalReference` (requis) |

Pour chaque ligne, la commande recherche un `product.product` dont `name` correspond exactement, puis écrit
`default_code = InternalReference`. Les produits introuvables ou ambigus (plusieurs correspondances) sont
listés sur stderr et ignorés. Le séparateur attendu est la virgule ; les valeurs contenant une virgule peuvent
être entourées de doubles guillemets.

Exemple de CSV :

```csv
Name,InternalReference
Pommes Golden,FR-POM-001
"Bananes, lot 1kg",FR-BAN-002
```

Exemple :

```bash
odoo articles update-internal-references --csv produits.csv
```

### `cooperators` — gérer les coopérateurs

#### `cooperators list`

Liste les coopérateurs (membres de `res.partner` ayant souscrit du capital via `account.invoice`
`is_capital_fundraising=true`, état `paid`).

| Option                  | Description                                                                                                       |
|-------------------------|-------------------------------------------------------------------------------------------------------------------|
| `--at-date DATE`        | Ne compte que les parts dont `date_invoice <= DATE` (format `jj/MM/aaaa`)                                         |
| `--output FORMAT`       | Format de sortie : `pretty` (défaut) ou `csv` (séparateur `;`)                                                    |
| `--no-email`            | N'afficher que les coopérateurs sans email                                                                        |
| `--duplicate-email`     | N'afficher que les coopérateurs partageant leur email avec au moins un autre ; les lignes sont groupées par email |
| `--group-by GROUP`      | Grouper la sortie. Valeur acceptée : `binome` — affiche le binôme (`is_associated_people=true`) sous le principal |
| `--sort-by COLUMN`      | Trier par colonne : `id`, `nom`, `prenom`, `email`, `adresse`, `parts`, `capital`, `inscription`                  |
| `--sort-direction DIR`  | Sens du tri : `asc` (défaut) ou `desc` ; appliqué uniquement avec `--sort-by`                                     |

La colonne `Inscription` correspond à la date de la première facture de souscription au capital (état `paid`),
formatée en `jj/MM/aaaa`.

Tri par défaut (sans `--sort-by`) : nom puis prénom (case-insensitive). Avec `--duplicate-email`, le tri devient
email, puis nom, puis prénom (pour grouper les doublons). Quand `--sort-by` est fourni, il prend le pas sur ces tris
par défaut.

Avec `--group-by binome`, chaque binôme est inséré juste après son coopérateur principal. La colonne `Id` contient
`└─→` (flèche à angle droit pointant du principal vers le binôme) et les colonnes `Parts` / `Capital` sont vides
(un binôme ne détient pas de parts).

Exemples :

```bash
odoo cooperators list
odoo cooperators list --at-date 31/12/2025 --output csv
odoo cooperators list --duplicate-email
odoo cooperators list --no-email
odoo cooperators list --group-by binome
odoo cooperators list --sort-by capital --sort-direction desc
odoo cooperators list --sort-by inscription
```

### `creneaux` — gérer les créneaux (`shift.template`)

#### `creneaux list`

Liste les modèles de créneaux du module `coop_shift`.

| Option                               | Description                                                                                                                                        |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `--output FORMAT`                    | Format de sortie : `pretty` (défaut) ou `csv` (séparateur `;`)                                                                                     |
| `--active-only` / `--no-active-only` | Inclure ou non les inactifs (défaut: actifs uniquement)                                                                                            |
| `--under-min`                        | N'afficher que les créneaux où `seats_reserved < seats_min` (les créneaux avec `seats_min = 0` sont exclus, car la règle est désactivée côté Odoo) |

Tri par défaut : semaine, puis date de début, puis nom.

Exemples :

```bash
odoo creneaux list
odoo creneaux list --output csv
odoo creneaux list --no-active-only
odoo creneaux list --under-min
```

## Tests

`mvn test` lance la suite. Les tests utilisent `@QuarkusMainTest` et un `WireMockOdooResource` qui simule l'endpoint
`/jsonrpc` d'Odoo. Voir `src/test/java/org/hoohoot/odoo/`.
