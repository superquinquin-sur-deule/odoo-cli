# odoo-cli

CLI Quarkus 3 (Java 21) qui interroge une instance Odoo via JSON-RPC. Inspiré
de https://github.com/superquinquin/odoo-scripts-ng.

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

### `cooperators` — gérer les coopérateurs

#### `cooperators list`

Liste les coopérateurs (membres de `res.partner` ayant souscrit du capital via `account.invoice`
`is_capital_fundraising=true`, état `paid`).

| Option              | Description                                                                                                       |
|---------------------|-------------------------------------------------------------------------------------------------------------------|
| `--at-date DATE`    | Ne compte que les parts dont `date_invoice <= DATE` (format `jj/MM/aaaa`)                                         |
| `--output FORMAT`   | Format de sortie : `pretty` (défaut) ou `csv` (séparateur `;`)                                                    |
| `--no-email`        | N'afficher que les coopérateurs sans email                                                                        |
| `--duplicate-email` | N'afficher que les coopérateurs partageant leur email avec au moins un autre ; les lignes sont groupées par email |

Tri par défaut : nom puis prénom (case-insensitive). Avec `--duplicate-email`, le tri devient email, puis nom, puis
prénom (pour grouper les doublons).

Exemples :

```bash
odoo cooperators list
odoo cooperators list --at-date 31/12/2025 --output csv
odoo cooperators list --duplicate-email
odoo cooperators list --no-email
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
