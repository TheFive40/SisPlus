const API_BASE = '/api/';

let allEmployees      = [];
let filteredEmployees = [];
const PER_PAGE        = 10;
let currentPage       = 1;
const DEFAULT_SALARY  = 2000_000;

const AVATAR_COLORS = [
    { bg:'rgba(30,58,138,.1)',  color:'#1E3A8A' },
    { bg:'rgba(13,148,136,.1)', color:'#0D9488' },
    { bg:'rgba(217,119,6,.1)',  color:'#B45309' },
    { bg:'rgba(225,29,72,.1)',  color:'#E11D48' },
    { bg:'rgba(124,58,237,.1)',color:'#7C3AED' },
    { bg:'rgba(5,150,105,.1)',  color:'#059669' },
];

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

function showAlert(message, type = 'success', container = 'alertContainer') {
    const icon = type === 'success'
        ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'
        : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
    const el = document.getElementById(container);
    el.innerHTML = `<div class="alert alert-${type}">${icon}<span>${message}</span></div>`;
    setTimeout(() => { el.innerHTML = ''; }, 5000);
}

function getInitials(name, lastName) {
    return ((name || '').charAt(0) + (lastName || '').charAt(0)).toUpperCase();
}

function formatCurrency(value) {
    return new Intl.NumberFormat('es-CO', {
        style: 'currency',
        currency: 'COP',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(value || 0);
}

async function loadEmployees() {
    try {
        const res = await fetch(`${API_BASE}users`);
        if (!res.ok) throw new Error();
        allEmployees = await res.json();
        applyFilter();
        document.getElementById('stateLoading').classList.add('hidden');
        document.getElementById('tableSection').classList.remove('hidden');
    } catch {
        document.getElementById('stateLoading').textContent = 'Error al cargar empleados.';
        showAlert('Error al cargar los empleados. Recarga la página.', 'error');
    }
}

function applyFilter() {
    const q = document.getElementById('searchInput').value.toLowerCase();
    filteredEmployees = allEmployees.filter(e =>
        `${e.id}`.includes(q) ||
        `${e.name} ${e.lastName}`.toLowerCase().includes(q) ||
        (e.cc || '').toLowerCase().includes(q)
    );
    currentPage = 1;
    renderTable();
    renderPagination();
}

function renderTable() {
    const tbody = document.getElementById('empTableBody');
    const empty = document.getElementById('stateEmpty');
    const start = (currentPage - 1) * PER_PAGE;
    const slice = filteredEmployees.slice(start, start + PER_PAGE);

    if (!filteredEmployees.length) {
        empty.classList.remove('hidden');
        tbody.innerHTML = '';
        return;
    }
    empty.classList.add('hidden');

    tbody.innerHTML = slice.map((emp, i) => {
        const col      = AVATAR_COLORS[(start + i) % AVATAR_COLORS.length];
        const initials = getInitials(emp.name, emp.lastName);
        return `
        <tr>
          <td style="font-size:11px;color:var(--gray-400)">#${emp.id}</td>
          <td>
            <div class="emp-name-cell">
              <div class="emp-avatar" style="background:${col.bg};color:${col.color}">${initials}</div>
              <span class="emp-fullname">${emp.name} ${emp.lastName}</span>
            </div>
          </td>
          <td>${emp.cc}</td>
          <td style="font-size:12px;color:var(--gray-500)">${emp.zkPin || '—'}</td>
          <td style="font-size:12px;color:var(--gray-700);font-weight:500">${formatCurrency(emp.salary || DEFAULT_SALARY)}</td>
          <td><span class="badge badge-active">Activo</span></td>
          <td>
            <div class="action-btns">
              <button class="btn btn-secondary btn-sm" onclick="openViewModal(${emp.id})">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                Ver
              </button>
              <button class="btn btn-secondary btn-sm" onclick="openEditModal(${emp.id})">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z"/></svg>
                Editar
              </button>
              <button class="btn btn-danger btn-sm" onclick="confirmDelete(${emp.id}, '${emp.name} ${emp.lastName}')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                Eliminar
              </button>
            </div>
          </td>
        </tr>`;
    }).join('');
}

function renderPagination() {
    const total     = filteredEmployees.length;
    const totalPgs  = Math.max(1, Math.ceil(total / PER_PAGE));
    const start     = total ? (currentPage - 1) * PER_PAGE + 1 : 0;
    const end       = Math.min(currentPage * PER_PAGE, total);

    document.getElementById('paginInfo').textContent = `Mostrando ${start}–${end} de ${total} empleados`;

    const ctrl = document.getElementById('paginControls');
    let pages = [];
    for (let i = 1; i <= totalPgs; i++) {
        if (i === 1 || i === totalPgs || (i >= currentPage - 1 && i <= currentPage + 1)) pages.push(i);
        else if (pages[pages.length - 1] !== '…') pages.push('…');
    }

    ctrl.innerHTML = `
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
    const total = Math.max(1, Math.ceil(filteredEmployees.length / PER_PAGE));
    currentPage = Math.min(Math.max(1, p), total);
    renderTable();
    renderPagination();
}

function openCreateModal() {
    document.getElementById('createForm').reset();
    document.getElementById('cSalary').value = DEFAULT_SALARY;
    document.getElementById('createAlert').innerHTML = '';
    document.getElementById('modalCreate').classList.add('active');
}
function closeCreateModal() { document.getElementById('modalCreate').classList.remove('active'); }

async function createEmployee(e) {
    e.preventDefault();
    const salary = parseFloat(document.getElementById('cSalary').value) || DEFAULT_SALARY;
    const body = {
        name:     document.getElementById('cName').value.trim(),
        lastName: document.getElementById('cLastName').value.trim(),
        cc:       document.getElementById('cCc').value.trim(),
        zkPin:    document.getElementById('cZkPin').value.trim() || null,
        salary:   salary
    };
    try {
        const res = await fetch(`${API_BASE}users`, {
            method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)
        });
        if (!res.ok) {
            const d = await res.json();
            throw new Error(d.message || 'Error al registrar');
        }
        closeCreateModal();
        showAlert('Empleado registrado exitosamente');
        loadEmployees();
    } catch (err) {
        showAlert(err.message || 'Error al registrar el empleado.', 'error', 'createAlert');
    }
}

function openViewModal(id) {
    const emp = allEmployees.find(e => e.id === id);
    if (!emp) return;
    const col      = AVATAR_COLORS[id % AVATAR_COLORS.length];
    const initials = getInitials(emp.name, emp.lastName);
    document.getElementById('viewAvatar').style.background = col.bg;
    document.getElementById('viewAvatar').style.color      = col.color;
    document.getElementById('viewAvatar').textContent      = initials;
    document.getElementById('viewId').textContent          = `#${emp.id}`;
    document.getElementById('viewFullName').textContent    = `${emp.name} ${emp.lastName}`;
    document.getElementById('vId').textContent             = emp.id;
    document.getElementById('vName').textContent           = emp.name;
    document.getElementById('vLastName').textContent       = emp.lastName;
    document.getElementById('vCc').textContent             = emp.cc;
    document.getElementById('vZkPin').textContent          = emp.zkPin || 'No configurado';
    document.getElementById('vSalary').textContent         = formatCurrency(emp.salary || DEFAULT_SALARY);
    document.getElementById('modalView').classList.add('active');
}
function closeViewModal() { document.getElementById('modalView').classList.remove('active'); }

function openEditFromView(id) {
    closeViewModal();
    openEditModal(id);
}

function openEditModal(id) {
    const emp = allEmployees.find(e => e.id === id);
    if (!emp) return;
    document.getElementById('eId').value       = emp.id;
    document.getElementById('eName').value     = emp.name;
    document.getElementById('eLastName').value = emp.lastName;
    document.getElementById('eCc').value       = emp.cc;
    document.getElementById('eZkPin').value    = emp.zkPin || '';
    document.getElementById('eSalary').value   = emp.salary || DEFAULT_SALARY;
    document.getElementById('editAlert').innerHTML = '';
    document.getElementById('modalEdit').classList.add('active');
}
function closeEditModal() { document.getElementById('modalEdit').classList.remove('active'); }

async function updateEmployee(e) {
    e.preventDefault();
    const id     = document.getElementById('eId').value;
    const salary = parseFloat(document.getElementById('eSalary').value) || DEFAULT_SALARY;
    const body = {
        name:     document.getElementById('eName').value.trim(),
        lastName: document.getElementById('eLastName').value.trim(),
        cc:       document.getElementById('eCc').value.trim(),
        zkPin:    document.getElementById('eZkPin').value.trim() || null,
        salary:   salary
    };
    try {
        const res = await fetch(`${API_BASE}users/${id}`, {
            method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)
        });
        if (!res.ok) {
            const d = await res.json();
            throw new Error(d.message || 'Error al actualizar');
        }
        closeEditModal();
        showAlert('Empleado actualizado exitosamente');
        loadEmployees();
    } catch (err) {
        showAlert(err.message || 'Error al actualizar el empleado.', 'error', 'editAlert');
    }
}

let pendingDeleteId = null;
function confirmDelete(id, name) {
    pendingDeleteId = id;
    document.getElementById('deleteEmpName').textContent = name;
    document.getElementById('modalDelete').classList.add('active');
}
function closeDeleteModal() { document.getElementById('modalDelete').classList.remove('active'); pendingDeleteId = null; }

async function deleteEmployee() {
    if (!pendingDeleteId) return;
    try {
        const res = await fetch(`${API_BASE}users/${pendingDeleteId}`, { method:'DELETE' });
        if (!res.ok) throw new Error();
        closeDeleteModal();
        showAlert('Empleado eliminado exitosamente');
        loadEmployees();
    } catch {
        showAlert('Error al eliminar el empleado.', 'error');
        closeDeleteModal();
    }
}

window.addEventListener('click', e => {
    ['modalCreate','modalView','modalEdit','modalDelete'].forEach(id => {
        if (e.target === document.getElementById(id)) {
            document.getElementById(id).classList.remove('active');
        }
    });
});

window.onload = () => { initClock(); loadEmployees(); };