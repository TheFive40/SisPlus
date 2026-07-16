const API_BASE = '/api/admin';

let allAdmins      = [];
let filteredAdmins = [];
const PER_PAGE     = 10;
let currentPage    = 1;

/* ── Sidebar / clock ── */
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const main    = document.getElementById('mainContent');
    if (window.innerWidth <= 900) {
        sidebar.classList.toggle('open');
    } else {
        sidebar.classList.toggle('closed');
        main.classList.toggle('expanded');
    }
}
window.addEventListener('resize', () => {
    if (window.innerWidth > 900) document.getElementById('sidebar').classList.remove('open');
});

function initClock() {
    const el = document.getElementById('topbarTime');
    if (!el) return;
    const tick = () => { el.textContent = new Date().toLocaleTimeString('es-CO', { hour:'2-digit', minute:'2-digit', hour12:true }); };
    tick(); setInterval(tick, 30000);
    const y = new Date().getFullYear();
    const fv = document.getElementById('footerVersion');
    const fm = document.getElementById('footerYearMain');
    if (fv) fv.textContent = `v2.0 · ${y}`;
    if (fm) fm.textContent = y;
}

/* ── Alerts ── */
function showAlert(msg, type = 'success', container = 'alertContainer') {
    const icon = type === 'success'
        ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'
        : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
    const el = document.getElementById(container);
    el.innerHTML = `<div class="alert alert-${type}">${icon}<span>${msg}</span></div>`;
    setTimeout(() => { el.innerHTML = ''; }, 5000);
}

/* ── Load ── */
async function loadAdmins() {
    try {
        const res = await fetch(`${API_BASE}/`);
        if (!res.ok) throw new Error();
        allAdmins = await res.json();
        applyFilter();
        document.getElementById('stateLoading').classList.add('hidden');
        document.getElementById('tableSection').classList.remove('hidden');
    } catch {
        document.getElementById('stateLoading').textContent = 'Error al cargar usuarios.';
        showAlert('Error al cargar los usuarios. Recarga la página.', 'error');
    }
}

function applyFilter() {
    const q = document.getElementById('searchInput').value.toLowerCase();
    filteredAdmins = allAdmins.filter(a => (a.username || '').toLowerCase().includes(q));
    currentPage = 1;
    renderTable();
    renderPagination();
}

/* ── Render ── */
function getLetters(username) {
    return (username || '').slice(0, 2).toUpperCase();
}

function renderTable() {
    const tbody = document.getElementById('admTableBody');
    const empty = document.getElementById('stateEmpty');
    const start = (currentPage - 1) * PER_PAGE;
    const slice = filteredAdmins.slice(start, start + PER_PAGE);

    if (!filteredAdmins.length) {
        empty.classList.remove('hidden');
        tbody.innerHTML = '';
        return;
    }
    empty.classList.add('hidden');

    tbody.innerHTML = slice.map(adm => {
        const letters = getLetters(adm.username);
        const isAdmin = adm.roles && adm.roles.includes('ADMIN');
        const roleBadge = isAdmin
            ? '<span class="badge badge-admin">Administrador</span>'
            : '<span class="badge badge-cargues">Gestión de Cargues</span>';
        return `
        <tr>
          <td>
            <div class="adm-name-cell">
              <div class="adm-avatar">${letters}</div>
              <span class="adm-username">${adm.username}</span>
            </div>
          </td>
          <td>${roleBadge}</td>
          <td><span class="badge badge-active">Activo</span></td>
          <td>
            <div class="action-btns">
              <button class="btn btn-danger btn-sm" onclick="confirmDelete('${adm.username}')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                Eliminar
              </button>
            </div>
          </td>
        </tr>`;
    }).join('');
}

function renderPagination() {
    const total    = filteredAdmins.length;
    const totalPgs = Math.max(1, Math.ceil(total / PER_PAGE));
    const start    = total ? (currentPage - 1) * PER_PAGE + 1 : 0;
    const end      = Math.min(currentPage * PER_PAGE, total);

    document.getElementById('paginInfo').textContent = `Mostrando ${start}–${end} de ${total} usuarios`;

    let pages = [];
    for (let i = 1; i <= totalPgs; i++) {
        if (i === 1 || i === totalPgs || (i >= currentPage - 1 && i <= currentPage + 1)) pages.push(i);
        else if (pages[pages.length - 1] !== '…') pages.push('…');
    }

    document.getElementById('paginControls').innerHTML = `
      <button class="page-btn" onclick="goPage(1)" ${currentPage===1?'disabled':''}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="11 17 6 12 11 7"/><polyline points="18 17 13 12 18 7"/></svg>
      </button>
      <button class="page-btn" onclick="goPage(${currentPage-1})" ${currentPage===1?'disabled':''}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
      ${pages.map(p => p === '…'
        ? `<span style="padding:0 4px;font-size:12px;color:var(--gray-400)">…</span>`
        : `<button class="page-btn ${p===currentPage?'active':''}" onclick="goPage(${p})">${p}</button>`
    ).join('')}
      <button class="page-btn" onclick="goPage(${currentPage+1})" ${currentPage===totalPgs?'disabled':''}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
      </button>
      <button class="page-btn" onclick="goPage(${totalPgs})" ${currentPage===totalPgs?'disabled':''}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="13 17 18 12 13 7"/><polyline points="6 17 11 12 6 7"/></svg>
      </button>`;
}

function goPage(p) {
    const total = Math.max(1, Math.ceil(filteredAdmins.length / PER_PAGE));
    currentPage = Math.min(Math.max(1, p), total);
    renderTable();
    renderPagination();
}

/* ── Password toggle ── */
function togglePwd(inputId, btnId) {
    const input = document.getElementById(inputId);
    const icon  = document.getElementById(btnId).querySelector('svg');
    if (input.type === 'password') {
        input.type = 'text';
        icon.innerHTML = `
            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
            <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
            <line x1="1" y1="1" x2="23" y2="23"/>`;
    } else {
        input.type = 'password';
        icon.innerHTML = `
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>`;
    }
}

/* ── Create ── */
function openCreateModal() {
    document.getElementById('createForm').reset();
    document.getElementById('createAlert').innerHTML = '';
    document.getElementById('modalCreate').classList.add('active');
}
function closeCreateModal() { document.getElementById('modalCreate').classList.remove('active'); }

async function createAdmin(e) {
    e.preventDefault();
    const username = document.getElementById('cUsername').value.trim();
    const password = document.getElementById('cPassword').value;
    const confirm  = document.getElementById('cConfirm').value;
    const role     = document.getElementById('cRole').value;

    if (password !== confirm) {
        showAlert('Las contraseñas no coinciden.', 'error', 'createAlert'); return;
    }
    if (password.length < 6) {
        showAlert('La contraseña debe tener al menos 6 caracteres.', 'error', 'createAlert'); return;
    }

    try {
        const res = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password, roles: [role] })
        });
        if (!res.ok) {
            const errText = await res.text().catch(() => 'Error del servidor');
            throw new Error(errText || 'Error al registrar');
        }
        closeCreateModal();
        showAlert('Usuario registrado exitosamente');
        loadAdmins();
    } catch (e) {
        showAlert(e.message || 'Error al registrar. El nombre de usuario puede estar en uso.', 'error', 'createAlert');
    }
}

/* ── Delete ── */
let pendingDelete = null;
function confirmDelete(username) {
    pendingDelete = username;
    document.getElementById('deleteAdmName').textContent = username;
    document.getElementById('modalDelete').classList.add('active');
}
function closeDeleteModal() { document.getElementById('modalDelete').classList.remove('active'); pendingDelete = null; }

async function deleteAdmin() {
    if (!pendingDelete) return;
    try {
        const res = await fetch(`${API_BASE}/${encodeURIComponent(pendingDelete)}`, { method: 'DELETE' });
        if (!res.ok) {
            const errText = await res.text().catch(() => 'Error del servidor');
            throw new Error(errText || 'Error al eliminar');
        }
        closeDeleteModal();
        showAlert('Usuario eliminado exitosamente');
        loadAdmins();
    } catch (e) {
        showAlert(e.message || 'Error al eliminar el usuario.', 'error');
        closeDeleteModal();
    }
}

/* ── Backdrop click ── */
window.addEventListener('click', e => {
    ['modalCreate', 'modalDelete'].forEach(id => {
        if (e.target === document.getElementById(id))
            document.getElementById(id).classList.remove('active');
    });
});

window.onload = () => { initClock(); loadAdmins(); };