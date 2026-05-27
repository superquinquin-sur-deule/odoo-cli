# Mise en place de l'EDI BIODIS

Rapport d'analyse — 2026-05-25

## 1. Contexte

Superquinquin souhaite automatiser les échanges EDI avec le fournisseur **BIODIS** (commandes, BL, mercuriale) sur le même modèle que ce qui existe déjà pour **DIAPAR**.

Sources analysées :
- Cahier des charges BIODIS *« EDI FTP V1.02 »* (Groupe ADINFO / DIFAGRO), 15 p.
- Module Odoo Druidoo `edi_purchase_*` du repo [AwesomeFoodCoops/odoo-production@9.0](https://github.com/AwesomeFoodCoops/odoo-production/tree/9.0/louve_addons).
- Configuration en production de l'instance Odoo Superquinquin (modèle `edi.config.system`).

Identifiants BIODIS fournis :
- Code client : `59163000`
- Serveur FTP : `edi.biodis.eu`
- Login : `INDEPEND`
- Mot de passe : `Mc3JuRz679`

## 2. Architecture EDI existante (DIAPAR)

### 2.1 Découpage en 3 modules

| Module | Rôle |
|---|---|
| `edi_purchase_config` | Modèles configurables : `edi.config.system`, `edi.mapping.lines`, `edi.price.mapping`, `edi.ble.mapping`, `purchase.edi.log`, flag `res.partner.is_edi` |
| `edi_purchase_base` | Cœur générique : génération fichier commande, push FTP, override `purchase.order.button_confirm`, parsing BL, cron de pull |
| `edi_purchase_diapar` | Spécialisation DIAPAR : valeurs par défaut des champs de config + override `_consolidate_products` (utilise `product_qty_package`) |

### 2.2 Champs clés de `edi.config.system`

- **FTP** : `ftp_host`, `ftp_port`, `ftp_login`, `ftp_password`
- **Chemins** : `csv_relative_in_path` (dépôt des commandes), `csv_relative_out_path` (récupération BL/tarifs)
- **Identification** : `customer_code`, `vrp_code`, `supplier_id` (m2o `res.partner` avec `is_edi=True`)
- **Wrappers fichier** : `constant_file_start`, `constant_file_end`, `header_code`, `lines_code`, `delivery_sign`
- **Nommage** : `po_text_file_pattern` (expression Python `eval`-uée → ex. `'LD%sH%s.C99' % get_datenow_format_for_file()`)
- **Pull** : `fnmatch_filter` (filtre fichiers entrants, ex. `CH11*`), `days` (fenêtre de fraîcheur)
- **Mappings** : `mapping_ids` (lignes commande), `price_mapping_ids` (mercuriale), `ble_mapping_ids` (BL)

### 2.3 Flux de génération de commande (positionnel)

Dans `edi_purchase_base/models/purchase.py` :

```python
def _prepare_data_lines(self, lines, edi):
    data = "%sA%sB%s%s%s" % (
        edi.constant_file_start,      # ex. "HDIAPAR "
        edi.vrp_code,                 # ex. "03"
        edi.customer_code,            # ex. "349415"
        self._get_data_from_mapping_config(lines, edi),
        edi.constant_file_end,        # ex. "*DIAPAR*DIAPAR"
    )
```

`_get_data_from_mapping_config` itère sur `edi.mapping_ids` et concatène le résultat de `eval(line.value)` pour chaque ligne — typiquement un fichier **positionnel à largeur fixe** où chaque mapping émet un fragment formaté avec `_fix_lenght(...)`.

Déclencheur : override de `PurchaseOrder.button_confirm` qui appelle `_process_send_ftp` si `partner_id.is_edi` est vrai. Le fichier est généré, déposé en FTP, et `purchase.edi.log` reçoit une entrée.

### 2.4 Flux de lecture des BL (positionnel)

Dans `edi_purchase_base/models/stock.py` :
- `cron_update_stock_picking` itère sur les `edi.config.system` (sans parent), ouvre le FTP, appelle `ftp_connection_pull_ble`.
- `ftp_connection_pull_ble` filtre les fichiers par `fnmatch("BLE*")` **codé en dur**, dézippe.
- `read_stock_picking_file` boucle ligne par ligne : compare `line[0] == header_code` puis `line[0] == lines_code`, extrait des slices via `line[sequence_start:sequence_end]`.
- Quand une quantité diffère de l'attendu, crée une proposition `picking.update` à valider par un opérateur.

### 2.5 Flux mercuriale

`ftp_connection_pull_prices` filtre par `fnmatch_filter` (ex. `CH11*`), dézippe, lit le fichier texte. Les lignes alimentent `supplier.price.list` via les slices définies dans `edi.price.mapping`.

### 2.6 Configuration actuelle (4 enregistrements DIAPAR)

| name | supplier_id | host | login | customer_code | in_path | out_path | filter |
|---|---|---|---|---|---|---|---|
| FTP Diapar | Diapar | `ftpclient.diapar.com` | `34941` | `349415` | `/Reception` | `/Envoi` | `CH11*` |
| FTP Epicerie | Diapar epicerie | idem | idem | idem | idem | idem | `CH*` |
| FTP Frais | Diapar Frais | idem | idem | idem | idem | idem | `CH*` |
| FTP Surgelés | Diapar Surgeles | idem | idem | idem | idem | idem | `CH*` |

## 3. Format BIODIS (synthèse du cahier des charges)

### 3.1 Spécifications générales

- **Format** : CSV texte, séparateur `;`, terminateur `\r\n` (« $0D0A »).
- **Encodage** : non précisé (vraisemblablement Latin-1, à confirmer).
- **Nommage** : `C<code_client>_<num>.txt` (≤ 60 caractères), ex. `C59163000_223310.txt`. Pour les BL : `C<code>_<num>_BL.txt`. Pour la mercuriale : `M_C<code>_YYYYMMDD.txt`.
- **Structure** : chaque ligne commence par un identifiant de segment (3 caractères : `@DI`, `PAR`, `ENT`, `COM`, `LIG`, `FTX`, `END`, `@ND`).
- **Limites** : 1 commande / fichier, 1 BL / fichier.

### 3.2 Arborescence FTP

Deux modes possibles, à choisir à la mise en place :

**Mode unique** (réponses dans un seul `/OUT`) :
```
Racine FTP/
├── IN/{encours,rejetes,traites}/   ← dépôt commandes ORDERS
├── OUT/traités/                    ← BL DESADV + MERCUR
└── Log/                            ← accusés de réception
```

**Mode multi-magasin** :
```
Racine FTP/
├── <Code Magasin 1>/traités/
├── <Code Magasin 2>/traités/
├── IN/{encours,rejetes,traites}/
└── Log/
```

Archivage automatique chez BIODIS : 2 mois.

### 3.3 Flux ORDERS (sortant — segments obligatoires en gras)

| Segment | Champs principaux | Notes |
|---|---|---|
| **`@DI`** | `@DI;FTP@EDI;ORDERS;STANDARD;[Test]` | En-tête fichier (5 champs) |
| **`PAR`** | `PAR;<EAN ou code client>;<libellé>;BIODIS;BIODIS;[livré];[libellé livré];[facturé];[libellé facturé]` | Partenaires (9 champs) |
| **`ENT`** | `ENT;<num commande>;<date cmd JJ/MM/AAAA>;<date livraison>;;;;;<réf pièce>` | Entête commande |
| `COM` | `COM;COM;<texte commentaire ≤500>` | Optionnel, paragraphe |
| **`LIG`** | `LIG;<n° ligne>;<EAN>;<code interne BIODIS>;<colisage>;<quantité>;<unité U/C/KG/P>;...` | Une ligne par produit |
| `FTX` | `FTX;COM;<commentaire ligne>` | Optionnel, attaché à la `LIG` précédente |
| **`END`** | `END` | Fin de commande |
| `@ND` | `@ND` | Optionnel, fin de fichier |

Exemple minimal du cahier des charges :
```
@DI;FTP@EDI;ORDERS;STANDARD;;
PAR;C0000000;;;;;;;;
ENT;223310; 26/12/2019; 26/12/2019;
LIG;1;3273220133989;CODEREF101;6,000;6,000;U;
LIG;2;3273220135402;CODEREF152;6,000;6,000;U;
END
```

**Recherche produit chez BIODIS** : priorité au code interne BIODIS, fallback sur l'EAN si l'article n'est pas trouvé. Si EAN+code introuvable → ligne rejetée. Unité hors `U|C|KG|P` → ligne rejetée.

**Rejet du fichier complet** (mail automatique) :
- Type non `ORDERS`, erreur de structure, champ obligatoire absent.
- Code client inconnu ou hors groupement.
- Numéro de commande déjà existant (pas de modification possible).
- Aucune ligne intégrable.

### 3.4 Flux DESADV (entrant — bon de livraison BIODIS → nous)

Structure identique aux ORDERS, segments différents :
- `@DI;FTP@EDI;DESADV;STANDARD;;`
- `PAR` : mêmes partenaires
- `ENT` : ajoute `<num expédition BIODIS>`, `<nombre lignes>`, `<colis>`, `<palettes>`, `<réf pièce>`
- `LIG` : ajoute `<quantité livrée>`, `<prix net>`, `<désignation>`, `<prix brut>`
- `END`, `@ND`

Seules les lignes effectivement livrées (quantité ≠ 0) sont présentes, **renumérotées** vs la commande.

### 3.5 Flux MERCUR (entrant — catalogue / tarifs)

- Segments : `@DI;FTP@EDI;MERCUR;STANDARD;;`, `PAR`, `MER`, `COM`, `LIM`, `FTX`, `END`, `@ND`.
- `MER` : `<date départ>`, `<date livraison>`, `<nombre lignes>`.
- `LIM` : EAN, code interne, colisage, unité, prix net, indicateur PROMO/MAE, catégorie, code bio, pays origine, région, désignation.

Une mercuriale par fichier. À intégrer dans `supplier.price.list`.

## 4. Écart entre l'existant et le besoin

### 4.1 Ce qui est réutilisable

- Modèle `edi.config.system` (FTP + identification + chemins + nommage) → s'applique tel quel.
- `ftp_connection_open` / `ftp_connection_close` → génériques.
- Flag `res.partner.is_edi` → réutilisable.
- Modèle `purchase.edi.log` → journalisation.
- Override `button_confirm` → on garde le déclencheur.
- Modèle `supplier.price.list` → s'applique pour la mercuriale.

### 4.2 Ce qui est incompatible

| Composant existant | Problème | Action |
|---|---|---|
| `_prepare_data_lines` | Format en dur `start+A+vrp+B+code+DATA+end` | Override complet en mode multi-segments |
| `_get_data_from_mapping_config` | Concat via `eval(line.value)` orientée positionnel | Inutile pour BIODIS — on peut s'en passer ou réutiliser `edi.mapping.lines` autrement |
| `read_stock_picking_file` | Parsing par slices `[start:end]` | Override : parser ligne par ligne avec `split(';')` et dispatch sur le préfixe segment |
| `ftp_connection_pull_ble` | Filtre `BLE*` codé en dur | Override : utiliser `fnmatch_filter` de la config (ex. `C59163000_*_BL.txt`) |
| `ftp_connection_pull_prices` | Attend un `.zip`, lit après dézippage | Override : pas de zip pour BIODIS, lecture directe `.txt` |
| `edi.price.mapping` (positions start/end) | Inadapté au CSV | Ignorable ; le parseur MERCUR utilisera les indices de colonnes |
| `edi.ble.mapping` (positions start/end) | Inadapté au CSV | Idem |

### 4.3 Ce qui manque entièrement

- Génération d'un fichier ORDERS multi-segments.
- Parsing CSV-segments (DESADV + MERCUR).
- Pull de la mercuriale (le module actuel ne pull que les prix DIAPAR via slices, et pas les MERCUR).
- Gestion du nommage `C<code>_<num>.txt` basé sur `purchase.order.name`.

## 5. Recommandations

### 5.1 Option retenue : nouveau module `edi_purchase_biodis`

Décalqué sur `edi_purchase_diapar` (~300-400 lignes Python attendues), structuré ainsi :

```
edi_purchase_biodis/
├── __openerp__.py            # depends: edi_purchase_base, edi_purchase_config, coop_purchase
├── __init__.py
├── models/
│   ├── __init__.py
│   ├── edi_config_system.py  # defaults BIODIS + helpers de formatage segments
│   ├── purchase.py           # override _prepare_data_lines (génération CSV-segments)
│   ├── stock.py              # override cron_update_stock_picking + read_stock_picking_file
│   └── supplier_price_list.py # nouveau cron cron_pull_biodis_mercuriale
├── data/
│   └── ir_cron.xml           # crons DESADV + MERCUR
└── views/
    └── res_partner_view.xml
```

### 5.2 Implémentation suggérée des méthodes critiques

**Génération ORDERS** (`models/purchase.py`) :

```python
def _prepare_data_lines(self, lines, edi):
    if edi.supplier_id.ref != '59163000' and 'biodis' not in (edi.name or '').lower():
        return super()._prepare_data_lines(lines, edi)

    segments = []
    segments.append(';'.join(['@DI', 'FTP@EDI', 'ORDERS', 'STANDARD', '']))
    segments.append(';'.join([
        'PAR', edi.customer_code, self.partner_id.name[:80],
        'BIODIS', 'BIODIS', '', '', '', '',
    ]))
    segments.append(';'.join([
        'ENT', self.name,
        self.date_order.strftime('%d/%m/%Y'),
        self.date_planned.strftime('%d/%m/%Y'),
        '', '', '', '', '',
    ]))
    for idx, (product, vals) in enumerate(lines.items(), start=1):
        segments.append(';'.join([
            'LIG', str(idx),
            product.barcode or '',
            vals['code'] or '',
            '',                                  # colisage
            ('%.3f' % vals['quantity']).replace('.', ','),
            'U',                                 # ou C/KG/P selon uom
            '', '', '', '',
        ]))
    segments.append('END')
    segments.append('@ND')
    return '\r\n'.join(segments) + '\r\n'
```

**Parsing DESADV** (`models/stock.py`) : remplacer la boucle `line[0] == header_code` par un `split(';')` et un dispatch par segment.

**Cron MERCUR** : pull `M_C<code>_*.txt`, parse, alimente `supplier.price.list` (en respectant le mapping LIM).

### 5.3 Configuration `edi.config.system` à créer

| Champ | Valeur |
|---|---|
| `name` | `FTP Biodis` |
| `supplier_id` | nouveau `res.partner` BIODIS (`is_edi=True`, `supplier=True`) |
| `ftp_host` | `edi.biodis.eu` |
| `ftp_port` | `21` (à confirmer) |
| `ftp_login` | `INDEPEND` |
| `ftp_password` | `Mc3JuRz679` |
| `customer_code` | `59163000` |
| `csv_relative_in_path` | `/IN` |
| `csv_relative_out_path` | `/OUT` (ou `/<code>/OUT` en mode multi) |
| `po_text_file_pattern` | `'C59163000_%s.txt' % self.name.replace('/', '_')` |
| `fnmatch_filter` | `C59163000_*_BL.txt` |
| `constant_file_start/end`, `vrp_code` | vides (non utilisés en mode BIODIS) |

### 5.4 Alternative écartée : implémentation dans ce CLI Java

Possible mais non recommandée : duplication du mapping produits, perte du déclencheur `button_confirm` automatique, nécessité d'un cron externe. À garder en plan B si l'accès au repo addon est bloqué.

## 6. Points à clarifier avant développement

1. **Mode FTP** : groupé (`/IN`, `/OUT`) ou multi-magasin (`/<code>/OUT`) ? Question à BIODIS lors du paramétrage initial.
2. **Port FTP** : 21 (FTP brut), FTPS explicite (21+AUTH TLS), FTPS implicite (990), ou SFTP ? À confirmer auprès du support DIFAGRO.
3. **Encodage des fichiers** : Latin-1 vs UTF-8. À tester avec un premier envoi (mode `Test=1` du segment `@DI`).
4. **Champ Odoo « code interne BIODIS »** : `product.supplierinfo.product_code` filtré sur `name = partner_biodis` semble être le bon mapping (cohérent avec ce qui est fait pour DIAPAR via `product._get_supplier_code_or_ean`).
5. **Numérotation commande (segment `ENT[2]`)** : utiliser `purchase.order.name` (unique, immutable une fois confirmé). Attention : pas de modification possible après envoi (rejet BIODIS si numéro déjà existant).
6. **Unité de mesure** : mapping Odoo `product.uom` → `U|C|KG|P`. Par défaut `U`. À définir cas par cas (ex. produits vendus au kilo → `KG`).
7. **Mode test** : commencer avec `@DI[5] = 1` pour valider les premiers échanges sans déclencher de vraies commandes côté BIODIS.

## 7. Plan d'attaque proposé

1. **Validation** des 7 points ci-dessus avec Superquinquin + BIODIS/DIFAGRO.
2. **Création du module** `edi_purchase_biodis` (~1 jour de dev) — fork du repo `AwesomeFoodCoops/odoo-production`, PR sur la branche `9.0`.
3. **Test FTP en local** avec `python -m pyftpdlib` + un faux serveur reproduisant l'arborescence BIODIS.
4. **Recette en mode `Test=1`** sur l'instance staging de Superquinquin : envoyer 2-3 commandes test, vérifier l'accusé de réception mail.
5. **Bascule production** une fois validé : créer le `res.partner` BIODIS, créer l'`edi.config.system`, activer `is_edi=True`.
6. **Monitoring** via `purchase.edi.log` et le répertoire FTP `Log/` côté BIODIS.

## 8. Annexes

### 8.1 Liens

- Repo addon : <https://github.com/AwesomeFoodCoops/odoo-production/tree/9.0/louve_addons>
- Module à dupliquer : `edi_purchase_diapar`
- Cahier des charges BIODIS : `~/Downloads/- CAHIER DES CHARGES BIODIS - EDI FTP -.pdf`

### 8.2 Récap des segments BIODIS

| Segment | Présent dans | Rôle |
|---|---|---|
| `@DI` | ORDERS, DESADV, MERCUR | Début fichier — type de flux |
| `PAR` | tous | Partenaires (client, livré, facturé, fournisseur) |
| `ENT` | ORDERS, DESADV | Entête commande / BL |
| `MER` | MERCUR | Entête mercuriale |
| `COM` | tous (optionnel) | Commentaire global |
| `LIG` | ORDERS, DESADV | Ligne produit |
| `LIM` | MERCUR | Ligne mercuriale |
| `FTX` | tous (optionnel) | Commentaire attaché à la ligne précédente |
| `END` | tous | Fin commande/BL/mercuriale |
| `@ND` | tous (optionnel) | Fin fichier |
