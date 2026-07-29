// Détecte l'affichage d'un formulaire purchase.order dans le web client Odoo 12
// (SPA à base de hash) et injecte un bouton « Export CSV fournisseur ».

const BTN_ID = 'foodcoop-export-po-csv';

/**
 * Lit l'état courant depuis location.hash.
 * Ex: #id=149&action=...&model=purchase.order&view_type=form
 * @returns {{model:string, viewType:string, id:number|null}}
 */
function readHashState() {
  const params = new URLSearchParams(location.hash.slice(1));
  const rawId = params.get('id');
  return {
    model: params.get('model') || '',
    viewType: params.get('view_type') || '',
    id: rawId ? parseInt(rawId, 10) : null,
  };
}

function isPurchaseOrderForm(state) {
  return (
    state.model === 'purchase.order' &&
    (state.viewType === 'form' || state.viewType === '') &&
    Number.isInteger(state.id)
  );
}

function buildButton() {
  const btn = document.createElement('button');
  btn.id = BTN_ID;
  btn.type = 'button';
  btn.className = 'btn btn-secondary foodcoop-export-btn';
  btn.textContent = 'Export CSV fournisseur';
  btn.addEventListener('click', onExportClick);
  return btn;
}

async function onExportClick(evt) {
  const btn = evt.currentTarget;
  const state = readHashState();
  if (!isPurchaseOrderForm(state)) return;

  const originalLabel = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Export en cours…';
  try {
    const { count, filename } = await exportPurchaseOrder(state.id);
    btn.textContent = count > 0 ? '✓ ' + count + ' lignes' : '✓ (aucune ligne)';
    console.info('[FoodCoop] Export généré :', filename, '(' + count + ' lignes)');
  } catch (err) {
    console.error('[FoodCoop] Échec export :', err);
    alert('Export impossible : ' + err.message);
    btn.textContent = originalLabel;
  } finally {
    setTimeout(() => {
      btn.disabled = false;
      btn.textContent = originalLabel;
    }, 2500);
  }
}

// Ancre d'injection : zone des boutons du control panel Odoo 12, avec repli.
function findAnchor() {
  return (
    document.querySelector('.o_cp_buttons') ||
    document.querySelector('.o_control_panel .o_cp_left') ||
    document.querySelector('.o_control_panel')
  );
}

let lastLoggedHash = null;

function syncButton() {
  const state = readHashState();
  const existing = document.getElementById(BTN_ID);

  // Diagnostic : logge l'état une fois par changement de hash.
  if (location.hash !== lastLoggedHash) {
    lastLoggedHash = location.hash;
    console.info('[FoodCoop] hash =', location.hash, '| état =', state,
      '| commande d\'achat ?', isPurchaseOrderForm(state));
  }

  if (!isPurchaseOrderForm(state)) {
    if (existing) existing.remove();
    return;
  }

  if (existing) return; // déjà injecté pour ce formulaire

  const anchor = findAnchor();
  const btn = buildButton();
  if (anchor) {
    anchor.appendChild(btn);
    console.info('[FoodCoop] bouton injecté dans', anchor.className);
  } else {
    // Repli : bouton flottant si le control panel n'est pas trouvé.
    btn.classList.add('foodcoop-export-btn--floating');
    document.body.appendChild(btn);
    console.info('[FoodCoop] bouton injecté en flottant (ancre control panel introuvable)');
  }
}

// Odoo 12 est une SPA : re-synchroniser sur navigation (hashchange) et sur
// re-rendus du DOM (MutationObserver throttlé via requestAnimationFrame).
window.addEventListener('hashchange', syncButton);

let pending = false;
const observer = new MutationObserver(() => {
  if (pending) return;
  pending = true;
  requestAnimationFrame(() => {
    pending = false;
    syncButton();
  });
});
observer.observe(document.body, { childList: true, subtree: true });

console.info('[FoodCoop] content script chargé sur', location.href);
syncButton();
