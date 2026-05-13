---
name: odoo-query
description: Interroger l'instance Odoo en JSON-RPC (lectures uniquement) en utilisant les credentials du fichier .env du projet. Utiliser ce skill dès qu'on a besoin d'inspecter le shape d'un modèle Odoo, lister des enregistrements, ou vérifier des champs avant d'écrire du code Java. Lectures seulement — jamais d'écriture (create/write/unlink/call_button).
---

# odoo-query — Interrogation Odoo en lecture seule

## Quand utiliser ce skill

- Avant d'implémenter une nouvelle commande pour fixer le shape exact des champs (cf. étape 1 du workflow TDD du CLAUDE.md).
- Pour vérifier rapidement la présence/valeur d'un champ sur un enregistrement réel.
- Pour stuber correctement les réponses dans `WireMockOdooResource`.

## Règle d'or — lecture seule

Ce skill autorise **uniquement** les méthodes Odoo de lecture :

- `search`, `search_read`, `search_count`, `read`, `fields_get`, `name_search`, `name_get`, `default_get`

Sont **interdits**, même en mode auto, même si l'utilisateur le demande de façon ambiguë :

- `create`, `write`, `unlink`, `copy`
- Toute `*_button`, action serveur, `execute_workflow`, `message_post`
- Tout appel `execute_kw` sur des wizards qui modifient l'état

Si l'utilisateur demande une écriture, sortir du skill et demander confirmation explicite hors auto-mode.

## Auto-mode

Tant qu'on reste sur les méthodes listées ci-dessus, on peut exécuter les `curl` sans demander confirmation, **y compris contre l'instance de production** définie dans `.env`. La lecture ne casse rien.

## Pattern d'appel

Charger les credentials depuis `.env` à la racine du projet, puis enchaîner login → `execute_kw`.

```bash
set -a && source .env && set +a

UID_=$(curl -s "$ODOO_URL/jsonrpc" -H 'Content-Type: application/json' \
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"params\":{\"service\":\"common\",\"method\":\"login\",\"args\":[\"$ODOO_DATABASE\",\"$ODOO_LOGIN\",\"$ODOO_PASSWORD\"]}}" \
  | jq '.result')

curl -s "$ODOO_URL/jsonrpc" -H 'Content-Type: application/json' \
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"params\":{\"service\":\"object\",\"method\":\"execute_kw\",\"args\":[\"$ODOO_DATABASE\",$UID_,\"$ODOO_PASSWORD\",\"<MODEL>\",\"search_read\",[<DOMAIN>],{\"fields\":[<FIELDS>],\"limit\":<N>}]}}" | jq
```

## Bonnes pratiques

- Toujours commencer par un `limit` petit (3–10) le temps de stabiliser la query.
- Inspecter les many2one : ils sortent en `[id, "Display Name"]` (ou `false` si vide).
- Pour découvrir le schéma : `fields_get` avec `{"attributes":["string","type","relation"]}`.
- Ne jamais hardcoder les credentials dans une commande ; toujours passer par `set -a && source .env && set +a`.
- Si la commande échoue avec `Session expired`, refaire le login (l'UID est valide tant que la session vit).

## Modèles fréquents dans ce projet

- `shift.template`, `shift.shift`, `shift.registration` (cf. module upstream `coop_shift`)
- `res.partner` pour les coopérateurs
- `product.template` / `product.product` pour les références internes
