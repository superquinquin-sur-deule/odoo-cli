// Client JSON-RPC minimal réutilisant la session Odoo de l'utilisateur connecté.
// Les appels sont same-origin (le content script tourne sur la page Odoo) et
// `credentials: 'include'` transmet le cookie de session. L'endpoint call_kw est
// de type `json` / auth='user' en Odoo 12 : aucun token CSRF requis.

/**
 * Appelle une méthode ORM Odoo via /web/dataset/call_kw.
 * @param {string} model  ex. "purchase.order"
 * @param {string} method ex. "read"
 * @param {Array}  args   arguments positionnels
 * @param {Object} kwargs arguments nommés
 * @returns {Promise<any>} le champ `result` de la réponse
 */
async function callKw(model, method, args, kwargs = {}) {
  let res;
  try {
    res = await fetch('/web/dataset/call_kw', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        jsonrpc: '2.0',
        method: 'call',
        params: { model, method, args, kwargs },
      }),
    });
  } catch (e) {
    throw new Error('Impossible de contacter Odoo (réseau). ' + e.message);
  }

  if (res.status === 401 || res.status === 403) {
    throw new Error('Session Odoo expirée — reconnectez-vous à Odoo puis réessayez.');
  }
  if (!res.ok) {
    throw new Error('Odoo a répondu ' + res.status + ' ' + res.statusText);
  }

  const data = await res.json();
  if (data.error) {
    const msg = (data.error.data && data.error.data.message) || data.error.message;
    // Une session expirée renvoie fréquemment une SessionExpiredException dans un 200.
    if (/session expired/i.test(JSON.stringify(data.error))) {
      throw new Error('Session Odoo expirée — reconnectez-vous à Odoo puis réessayez.');
    }
    throw new Error(msg || 'Erreur Odoo inconnue');
  }
  return data.result;
}
