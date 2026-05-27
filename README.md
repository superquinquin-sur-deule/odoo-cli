# odoo-cli

CLI Quarkus 3 (Java 21) qui interroge une instance Odoo v12 + Module FoodCoop via JSON-RPC.

## Sommaire

- [Installation](#installation)
  - [Mise à jour](#mise-à-jour)
- [Configuration](#configuration)
- [Lancement](#lancement)
- [Commandes](#commandes)
  - [`articles` — gérer les articles (`product.template`)](#articles--gérer-les-articles-producttemplate)
    - [`articles update-internal-references`](#articles-update-internal-references)
  - [`barcode-rules` — gérer les règles de code-barres](#barcode-rules--gérer-les-règles-de-code-barres)
    - [`barcode-rules list`](#barcode-rules-list)
    - [`barcode-rules test`](#barcode-rules-test)
  - [`cooperators` — gérer les coopérateurs](#cooperators--gérer-les-coopérateurs)
    - [`cooperators list`](#cooperators-list)
    - [`cooperators reset-ftop-counter`](#cooperators-reset-ftop-counter)
  - [`creneaux` — gérer les créneaux (`shift.template`)](#creneaux--gérer-les-créneaux-shifttemplate)
    - [`creneaux list`](#creneaux-list)
    - [`creneaux create-services`](#creneaux-create-services)
    - [`creneaux confirm-services`](#creneaux-confirm-services)
    - [`creneaux adjust-ftop-seats`](#creneaux-adjust-ftop-seats)
    - [`creneaux alert`](#creneaux-alert)
- [Tests](#tests)

## Installation

Installation rapide de la dernière version dans `~/.local/bin` (le script détecte l'OS/arch — Linux x86_64, macOS Intel, macOS Apple Silicon) :

```bash
curl -fsSL https://raw.githubusercontent.com/superquinquin-sur-deule/odoo-cli/main/install.sh | sh
```

Le script télécharge la dernière release GitHub, place le binaire dans `~/.local/bin/odoo-cli` et le rend exécutable.
Assurez-vous que `~/.local/bin` est dans votre `PATH` (le script vous le rappellera sinon).

### Mise à jour

Une fois installé, le binaire peut se mettre à jour lui-même :

```bash
odoo-cli update            # télécharge et remplace le binaire courant (ne fait rien si déjà à jour)
odoo-cli update --check    # affiche la version actuelle, la dernière disponible, et indique si une mise à jour est nécessaire
```

La commande `update` n'est disponible que sur le binaire natif (le remplacement in-place du binaire `java` n'a pas de sens en mode JVM).

## Configuration

Les credentials sont lus depuis `application.properties` les variables d'environnement ou un fichier `.env` à la racine :

| Property               | Env Var              | Description                                     | 
|------------------------|----------------------|-------------------------------------------------|
| `odoo.url`             | ODOO_URL             | URL de l'instance (ex: `http://localhost:8069`) |
| `odoo.database`        | ODOO_DATABASE        | Nom de la base                                  |
| `odoo.login`           | ODOO_LOGIN           | Login utilisateur                               |
| `odoo.password`        | ODOO_PASSWORD        | Mot de passe                                    |
| `odoo.timeout-seconds` | ODOO_TIMEOUT_SECONDS | Timeout HTTP (défaut: 60)                       |

## Lancement

En dev : `mvn quarkus:dev -Dquarkus.args="<commande> <sous-commande> [options]"`.
En package : `java -jar target/quarkus-app/quarkus-run.jar <commande> ...`.
Le cli : `odoo-cli <commande> ...`

## Commandes

### `articles` — gérer les articles (`product.template`)

#### `articles update-internal-references`

Met à jour en masse des champs de `product.template` (référence interne, catégorie, règle de code-barre, base)
depuis un fichier CSV. Les produits sont identifiés par leur External ID (`ir.model.data`) ; chaque colonne
optionnelle laissée vide est ignorée pour la ligne concernée.

| Option         | Description                                                                                                |
|----------------|------------------------------------------------------------------------------------------------------------|
| `--csv FILE`   | Fichier CSV à 6 colonnes avec en-tête `ExternalId,Name,Category,InternalReference,BarcodeRule,BarcodeBase` |
| `--dry-run`    | Simule l'exécution sans écrire dans Odoo                                                                   |

Colonnes :

- `ExternalId` — External ID (`module.name`) du `product.template` à mettre à jour ; requis.
- `Name` — utilisé uniquement à des fins d'affichage dans les logs.
- `Category` — External ID d'une `product.category` ; renseigne `categ_id` quand fourni.
- `InternalReference` — valeur écrite dans `default_code` quand fournie.
- `BarcodeRule` — nom exact d'une `barcode.rule` ; renseigne `barcode_rule_id` quand fourni.
- `BarcodeBase` — entier écrit dans `barcode_base` quand fourni.

Les External IDs (produits et catégories) sont résolus en batch via `ir.model.data` ; les noms de règles de
code-barre sont résolus en batch via `barcode.rule`. Les lignes dont l'External ID est introuvable sont listées
sur stderr et ignorées. Le séparateur attendu est la virgule ; les valeurs contenant une virgule peuvent être
entourées de doubles guillemets.

Exemple de CSV :

```csv
ExternalId,Name,Category,InternalReference,BarcodeRule,BarcodeBase
__export__.product_template_3802,Ail sec BIO,__export__.product_category_146,500,Price Look Up Codes (PLU Codes),500
__export__.product_template_34039_7d5bebe8,Aillet botte BIO,__export__.product_category_146,,,
```

Exemple :

```bash
odoo-cli articles update-internal-references --csv produits.csv
odoo-cli articles update-internal-references --csv produits.csv --dry-run
```

### `barcode-rules` — gérer les règles de code-barres

#### `barcode-rules list`

Liste les règles (`barcode.rule`) attachées à la nomenclature par défaut (`barcode.nomenclature`, première trouvée).

| Option                  | Description                                                                                  |
|-------------------------|----------------------------------------------------------------------------------------------|
| `--output FORMAT`       | Format de sortie : `pretty` (défaut) ou `csv` (séparateur `;`)                               |
| `--sort-by COLUMN`      | Trier par colonne : `nom`, `type`, `encodage`, `modele`, `date`, `sequence`, `transformer` (défaut: `sequence`) |
| `--sort-direction DIR`  | Sens du tri : `asc` (défaut) ou `desc`                                                       |

Colonnes affichées : `Nom`, `Type`, `Encodage`, `Modèle`, `Date création` (formatée `jj/MM/aaaa`), `Séquence`, `Transformer` (expression `transform_expr`, vide si non définie).

Exemples :

```bash
odoo-cli barcode-rules list
odoo-cli barcode-rules list --output csv
odoo-cli barcode-rules list --sort-by nom
odoo-cli barcode-rules list --sort-by date --sort-direction desc
```

#### `barcode-rules test`

Teste un code-barres contre les règles de la nomenclature par défaut, dans l'ordre de séquence ascendant. La
règle appliquée est la **première** dont l'encodage et le motif correspondent (comportement de
`barcode.nomenclature.parse_barcode` côté Odoo).

| Option            | Description                                                           |
|-------------------|-----------------------------------------------------------------------|
| `BARCODE`         | Code-barres à tester (argument positionnel)                           |
| `--output FORMAT` | Format de sortie : `pretty` (défaut) ou `csv` (séparateur `;`)        |

Colonnes affichées : `Séq.`, `Nom`, `Type`, `Encodage`, `Modèle`, `Match`, `Valeur`.

- `Match` :
  - `→` pour la règle appliquée (premier match par séquence)
  - `✓` pour les autres règles qui matcheraient aussi mais ne s'appliquent pas (séquence plus grande)
  - `✗` pour celles qui ne matchent pas (encodage incompatible ou motif différent)
- `Valeur` : valeur numérique extraite quand le motif contient `{N..D..}` (les `N` forment la partie entière, les `D` la partie décimale).

L'encodage est validé strictement : `ean13` exige 13 chiffres avec la somme de contrôle correcte, `ean8` huit
chiffres avec checksum, `upca` douze chiffres avec checksum, `any` accepte n'importe quoi. Un code-barres trop
court par rapport au motif est considéré comme ne matchant pas.

Une ligne stderr résume la règle appliquée (avec la valeur extraite le cas échéant) ou indique
« Aucune règle applicable ».

Exemples :

```bash
odoo-cli barcode-rules test 0211100012347
odoo-cli barcode-rules test "02154654654"
odoo-cli barcode-rules test PACK0001 --output csv
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
| `--exclude-binomes`     | Exclut les coopérateurs qui sont eux-mêmes des binômes (`is_associated_people=true`)                              |
| `--status STATUS`       | Filtre par statut coopératif (`cooperative_state` d'Odoo) ; répétable pour combiner plusieurs statuts             |
| `--sort-by COLUMN`      | Trier par colonne : `id`, `nom`, `prenom`, `email`, `adresse`, `parts`, `capital`, `inscription`, `statut`        |
| `--sort-direction DIR`  | Sens du tri : `asc` (défaut) ou `desc` ; appliqué uniquement avec `--sort-by`                                     |

La colonne `Statut` correspond au champ `cooperative_state` d'Odoo. Valeurs possibles : `not_concerned`, `unsubscribed`,
`exempted`, `vacation`, `up_to_date`, `alert`, `suspended`, `delay`, `blocked`, `unpayed`.

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
odoo-cli cooperators list
odoo-cli cooperators list --at-date 31/12/2025 --output csv
odoo-cli cooperators list --duplicate-email
odoo-cli cooperators list --no-email
odoo-cli cooperators list --group-by binome
odoo-cli cooperators list --sort-by capital --sort-direction desc
odoo-cli cooperators list --sort-by inscription
odoo-cli cooperators list --status up_to_date
odoo-cli cooperators list --status alert --status suspended
odoo-cli cooperators list --exclude-binomes
```

#### `cooperators reset-ftop-counter`

Remet à 0 les compteurs FTOP des coopérateurs (`res.partner.display_ftop_points`) en marquant `ignored=true`
sur tous les `shift.counter.event` de type `ftop` non encore ignorés, et en leur ajoutant le motif
**« Services avant ouverture »** (`shift.counter.event.reason` id=1). Les events sont conservés (audit trail),
seul leur impact sur le compteur est neutralisé.

| Option              | Description                                                                       |
|---------------------|-----------------------------------------------------------------------------------|
| `--before-date DATE` | Ne traite que les events dont `create_date <= DATE 23:59:59` (jj/MM/aaaa)        |
| `--partner-id ID`   | Filtre par id de coopérateur (répétable) ; ne traite que les events de ces partners |
| `--dry-run`         | Simule l'exécution sans écrire dans Odoo                                          |

Exemples :

```bash
odoo-cli cooperators reset-ftop-counter --dry-run
odoo-cli cooperators reset-ftop-counter
odoo-cli cooperators reset-ftop-counter --before-date 31/08/2026
odoo-cli cooperators reset-ftop-counter --partner-id 1689 --partner-id 1879
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
odoo-cli creneaux list
odoo-cli creneaux list --output csv
odoo-cli creneaux list --no-active-only
odoo-cli creneaux list --under-min
```

#### `creneaux create-services`

Crée les services (`shift.shift`) à partir des `shift.template` entre deux dates, en appelant
`create_shifts_from_template(after, before)` côté Odoo (équivalent du wizard `create.shifts.wizard`).

| Option              | Description                              |
|---------------------|------------------------------------------|
| `--begin-date DATE` | Date de début (jj/MM/aaaa) — **requise** |
| `--end-date DATE`   | Date de fin (jj/MM/aaaa) — **requise**   |

Exemple :

```bash
odoo-cli creneaux create-services --begin-date 06/04/2026 --end-date 20/07/2026
```

#### `creneaux confirm-services`

Confirme les services (`shift.shift`) en état `draft` dont la date de début est comprise entre les deux dates,
en appelant `button_confirm` côté Odoo.

| Option              | Description                              |
|---------------------|------------------------------------------|
| `--begin-date DATE` | Date de début (jj/MM/aaaa) — **requise** |
| `--end-date DATE`   | Date de fin (jj/MM/aaaa) — **requise**   |

Exemple :

```bash
odoo-cli creneaux confirm-services --begin-date 20/04/2026 --end-date 20/07/2026
```

#### `creneaux adjust-ftop-seats`

Aligne le nombre de places volants (FTOP) de chaque service confirmé entre deux dates, en écrivant sur le ticket
FTOP `seats_max = (seats_max ABCD du template) - (seats_reserved ABCD du service) + 1`. Permet de récupérer
automatiquement les places ABCD non pourvues pour les ouvrir aux volants, plus une place tampon pour rester
attractif (sinon un créneau plein côté ABCD afficherait `seats_max = 0` côté FTOP).

Par défaut, seuls les services en `state='confirm'` sont traités. Avec `--include-draft`, les services en
`state='draft'` sont inclus aussi (utile pour la semaine suivante : le cron upstream `Shifts Confirmation` ne
confirme que les services à J+5 max). La valeur calculée est clampée à 0 (jamais négative). Les services dont
le ticket standard, le ticket FTOP ou le template ABCD est introuvable sont listés sur stderr et ignorés.

| Option              | Description                                                                  |
|---------------------|------------------------------------------------------------------------------|
| `--begin-date DATE` | Date de début (jj/MM/aaaa) — **requise**                                     |
| `--end-date DATE`   | Date de fin (jj/MM/aaaa) — **requise**                                       |
| `--dry-run`         | Simule sans écrire dans Odoo                                                 |
| `--include-draft`   | Traite aussi les services en brouillon (state=draft), en plus des confirmés  |

Exemples :

```bash
odoo-cli creneaux adjust-ftop-seats --begin-date 20/04/2026 --end-date 20/07/2026
odoo-cli creneaux adjust-ftop-seats --begin-date 20/04/2026 --end-date 20/07/2026 --dry-run
odoo-cli creneaux adjust-ftop-seats --begin-date 24/05/2026 --end-date 31/05/2026 --include-draft
```

#### `creneaux alert`

Liste les services (`shift.shift`) en `draft` ou `confirm` dont `date_begin` est compris entre maintenant et
J+1 23:59 et pour lesquels `seats_reserved < seats_min` (les services avec `seats_min = 0` sont exclus). Utile
pour repérer rapidement les créneaux qui risquent de manquer de monde dans les prochaines 24-48h.

| Option              | Description                                                              |
|---------------------|--------------------------------------------------------------------------|
| `--output FORMAT`   | Format de sortie : `pretty` (défaut), `csv` (séparateur `;`) ou `json`   |
| `--begin-date DATE` | Date de début (jj/MM/aaaa) ; défaut: maintenant (00:00:00 si fourni)     |
| `--end-date DATE`   | Date de fin (jj/MM/aaaa) ; défaut: J+1 23:59 (23:59:59 si fourni)        |

Tri par date de début, puis nom.

Exemples :

```bash
odoo-cli creneaux alert
odoo-cli creneaux alert --output csv
odoo-cli creneaux alert --output json
odoo-cli creneaux alert --begin-date 19/05/2026 --end-date 26/05/2026
```

## Tests

`mvn test` lance la suite. Les tests utilisent `@QuarkusMainTest` et un `WireMockOdooResource` qui simule l'endpoint
`/jsonrpc` d'Odoo. Voir `src/test/java/org/hoohoot/odoo/`.
