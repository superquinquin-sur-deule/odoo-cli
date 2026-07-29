# Extension navigateur — Odoo FoodCoop, outils achats

Extension Chromium (Manifest V3) qui ajoute des outils aux utilisateurs d'Odoo v12 +
module FoodCoop. Elle réutilise la **session Odoo de l'utilisateur déjà connecté** :
aucun identifiant n'est stocké.

## Fonctionnalités

- **Export CSV fournisseur** : sur le formulaire d'une commande d'achat
  (`purchase.order`), un bouton « Export CSV fournisseur » génère un fichier CSV
  téléchargeable (une ligne par article : code fournisseur, désignation, quantité,
  unité, prix unitaire, sous-total, n° de commande, date de livraison).

## Installation (mode développeur)

1. Ouvrir `chrome://extensions` (Chrome / Chromium / Edge).
2. Activer le **Mode développeur** (en haut à droite).
3. Cliquer **Charger l'extension non empaquetée** et sélectionner ce dossier
   (`extension/`).
4. Aller sur Odoo (`https://sqq-sdl.foodcoop12.trobz.com`), se connecter, puis ouvrir
   une commande d'achat. Le bouton apparaît dans la barre d'actions du formulaire.

> ⚠️ Les icônes (`icons/icon-16.png`, `-48`, `-128`) doivent être présentes pour que
> Chrome charge l'extension sans avertissement. Ajoutez trois PNG carrés (n'importe
> quel visuel) aux tailles indiquées.

## Périmètre / autres instances

L'extension ne s'active que sur les hôtes déclarés dans `manifest.json` :

```json
"host_permissions": ["https://*.foodcoop12.trobz.com/*"]
```

Pour une autre instance Odoo, ajouter son hôte à la fois dans `host_permissions` et
dans `content_scripts[].matches`, puis recharger l'extension.

## Architecture

Pas de build : JavaScript vanilla, fichiers injectés dans l'ordre déclaré par le
manifeste (ils partagent le même scope isolé).

| Fichier            | Rôle                                                                 |
| ------------------ | ------------------------------------------------------------------- |
| `src/odoo-rpc.js`  | `callKw()` — appel `/web/dataset/call_kw` avec le cookie de session |
| `src/csv.js`       | Construction CSV (`;`, échappement, BOM UTF-8) + téléchargement      |
| `src/export.js`    | `exportPurchaseOrder(id)` — 2 lectures RPC + mapping colonnes        |
| `src/content.js`   | Détection du formulaire `purchase.order` + injection du bouton       |
| `styles/button.css`| Style du bouton (dont repli flottant)                               |

Le format CSV suit les conventions du CLI Java du repo
(`src/main/java/org/hoohoot/odoo/format/CsvFormatter.java`).
