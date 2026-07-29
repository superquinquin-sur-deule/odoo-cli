// Logique métier : lit une commande d'achat + ses lignes via 2 appels RPC,
// mappe vers un CSV générique lisible et déclenche le téléchargement.

const PO_FIELDS = ['name', 'partner_id', 'date_planned', 'order_line'];

const LINE_FIELDS = [
  'vendor_product_code',
  'product_id',
  'name',
  'product_qty',
  'product_uom',
  'price_unit',
  'price_subtotal',
];

const CSV_HEADERS = [
  'Code fournisseur',
  'Désignation',
  'Quantité',
  'Unité',
  'Prix unitaire',
  'Sous-total',
  'N° commande',
  'Date livraison',
];

// Un many2one lu via `read` vaut [id, "Libellé"] ou false s'il est vide.
function m2oLabel(value) {
  return Array.isArray(value) ? value[1] : '';
}

function m2oId(value) {
  return Array.isArray(value) ? value[0] : null;
}

// Formate un nombre à la française (virgule décimale) pour Excel FR.
function frNumber(value) {
  if (value === null || value === undefined || value === false) return '';
  return String(value).replace('.', ',');
}

/**
 * Exporte la commande d'achat `poId` en CSV.
 * @param {number} poId
 */
async function exportPurchaseOrder(poId) {
  const poRecords = await callKw('purchase.order', 'read', [[poId], PO_FIELDS]);
  if (!poRecords || poRecords.length === 0) {
    throw new Error('Commande introuvable (id ' + poId + ').');
  }
  const po = poRecords[0];

  const lineIds = po.order_line || [];
  let lines = [];
  if (lineIds.length > 0) {
    lines = await callKw('purchase.order.line', 'read', [lineIds, LINE_FIELDS]);
  }

  const partnerName = m2oLabel(po.partner_id);

  const rows = lines.map((l) => [
    l.vendor_product_code || '',
    m2oLabel(l.product_id) || l.name || '',
    frNumber(l.product_qty),
    m2oLabel(l.product_uom),
    frNumber(l.price_unit),
    frNumber(l.price_subtotal),
    po.name || '',
    po.date_planned || '',
  ]);

  const csv = buildCsv(CSV_HEADERS, rows);
  const filename = safeFileName(po.name + '_' + partnerName) + '.csv';
  downloadTextFile(filename, csv);

  return { count: rows.length, filename };
}
