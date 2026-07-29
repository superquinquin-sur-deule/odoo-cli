// Construction de CSV alignée sur les conventions du CLI Java
// (org.hoohoot.odoo.format.CsvFormatter) : séparateur ';', guillemets si la
// valeur contient ';', '"' ou un saut de ligne, avec doublement des '"'.

const CSV_SEP = ';';

/**
 * Échappe une valeur pour une cellule CSV.
 * @param {*} value
 * @returns {string}
 */
function csvEscape(value) {
  if (value === null || value === undefined) return '';
  const s = String(value);
  if (s.includes(CSV_SEP) || s.includes('"') || s.includes('\n')) {
    return '"' + s.replace(/"/g, '""') + '"';
  }
  return s;
}

/**
 * Construit le texte CSV (avec BOM UTF-8 pour Excel FR).
 * @param {string[]} headers
 * @param {Array<Array<*>>} rows
 * @returns {string}
 */
function buildCsv(headers, rows) {
  const lines = [];
  lines.push(headers.map(csvEscape).join(CSV_SEP));
  for (const row of rows) {
    lines.push(row.map(csvEscape).join(CSV_SEP));
  }
  // BOM UTF-8 : garantit l'affichage correct des accents à l'ouverture dans Excel.
  return '﻿' + lines.join('\r\n');
}

/**
 * Déclenche le téléchargement d'un contenu texte en tant que fichier.
 * @param {string} filename
 * @param {string} content
 */
function downloadTextFile(filename, content) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  // Laisse au navigateur le temps de démarrer le téléchargement avant de révoquer.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Nettoie une chaîne pour un nom de fichier sûr.
 * @param {string} name
 * @returns {string}
 */
function safeFileName(name) {
  return String(name || 'export').replace(/[\\/:*?"<>|]+/g, '_').trim();
}
