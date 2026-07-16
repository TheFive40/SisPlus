/**
 * SisPlus — Gestión de Cargues
 * Frontend controller for the visual fleet dashboard.
 */

/* ── State ── */
const App = {
    vehicles: [],
    drivers: [],
    loads: [],
    expenses: [],
    categories: [],
    selectedVehicle: null,
    selectedLoad: null,
    selectedExpense: null,
    currentDate: getBogotaDateString(),
    deleteHandler: null,
    expensePage: 1,
    expensePageSize: 10
};

function getBogotaDateString(date = new Date()) {
    return date.toLocaleDateString('en-CA', { timeZone: 'America/Bogota' });
}

function getBogotaStartOfWeek() {
    const now = new Date();
    const bogota = new Date(now.toLocaleString('en-US', { timeZone: 'America/Bogota' }));
    const day = bogota.getDay();
    const diff = bogota.getDate() - day + (day === 0 ? -6 : 1);
    const monday = new Date(bogota.setDate(diff));
    return getBogotaDateString(monday);
}

function getBogotaStartOfMonth() {
    const now = new Date();
    const bogota = new Date(now.toLocaleString('en-US', { timeZone: 'America/Bogota' }));
    bogota.setDate(1);
    return getBogotaDateString(bogota);
}

/* ── Number formatting helpers ── */
function formatNumberInput(input) {
    const raw = input.value.replace(/\D/g, '');
    if (!raw) {
        input.value = '';
        return;
    }
    input.value = new Intl.NumberFormat('es-CO').format(parseInt(raw, 10));
}

function parseFormattedNumber(value) {
    if (!value) return 0;
    const clean = String(value).replace(/\./g, '').replace(/,/g, '.');
    const num = parseFloat(clean);
    return isNaN(num) ? 0 : num;
}

/* ── Icons ── */
const Icons = {
    truck: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="1" y="6" width="13" height="10" rx="1"/><path d="M14 11h3l4 4v1a1 1 0 0 1-1 1h-1"/><circle cx="6" cy="17" r="2"/><circle cx="18" cy="17" r="2"/></svg>`,
    person: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
    money: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>`,
    check: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>`,
    edit: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z"/></svg>`,
    trash: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>`,
    plus: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>`
};

/* ── API ── */
async function api(url, options = {}) {
    const isFormData = options.body instanceof FormData;
    const defaultHeaders = isFormData ? {} : { 'Content-Type': 'application/json' };
    const res = await fetch(url, {
        headers: { ...defaultHeaders, ...(options.headers || {}) },
        ...options
    });
    if (!res.ok) {
        let msg = `Error ${res.status}`;
        try {
            const txt = await res.text();
            if (txt) {
                try {
                    const json = JSON.parse(txt);
                    msg = json.message || json.error || txt;
                } catch {
                    msg = txt;
                }
            }
        } catch {}
        throw new Error(msg);
    }
    if (res.status === 204) return null;
    return res.json().catch(() => null);
}

const API = {
    getDrivers: () => api('/api/cargos/drivers'),
    createDriver: data => api('/api/cargos/drivers', { method: 'POST', body: JSON.stringify(data) }),
    updateDriver: (id, data) => api(`/api/cargos/drivers/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteDriver: id => api(`/api/cargos/drivers/${id}`, { method: 'DELETE' }),

    getVehicles: () => api('/api/cargos/vehicles'),
    createVehicle: data => api('/api/cargos/vehicles', { method: 'POST', body: JSON.stringify(data) }),
    updateVehicle: (id, data) => api(`/api/cargos/vehicles/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteVehicle: id => api(`/api/cargos/vehicles/${id}`, { method: 'DELETE' }),

    getLoads: date => api(`/api/cargos?date=${date}`),
    createLoad: data => api('/api/cargos', { method: 'POST', body: JSON.stringify(data) }),
    updateLoad: (id, data) => api(`/api/cargos/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteLoad: id => api(`/api/cargos/${id}`, { method: 'DELETE' }),
    markDelivered: id => api(`/api/cargos/${id}/deliver`, { method: 'POST' }),
    markPending: id => api(`/api/cargos/${id}/pending`, { method: 'POST' }),

    saveSettlement: data => api('/api/cargos/settlements', { method: 'POST', body: JSON.stringify(data) }),
    saveBulkSettlement: data => api('/api/cargos/settlements/bulk', { method: 'POST', body: JSON.stringify(data) }),

    getReport: date => api(`/api/cargos/report?date=${date}`),
    sendReport: date => api(`/api/cargos/report/send?date=${date}`, { method: 'POST' }),

    getExpenses: params => api(`/api/cargos/expenses?${params}`),
    getExpenseSummary: params => api(`/api/cargos/expenses/summary?${params}`),
    createExpense: formData => api('/api/cargos/expenses', { method: 'POST', body: formData }),
    updateExpense: (id, formData) => api(`/api/cargos/expenses/${id}`, { method: 'PUT', body: formData }),
    deleteExpense: id => api(`/api/cargos/expenses/${id}`, { method: 'DELETE' }),
    downloadExpenseAttachment: id => `/api/cargos/expenses/${id}/attachment`,

    getCategories: () => api('/api/cargos/expenses/categories'),
    createCategory: data => api('/api/cargos/expenses/categories', { method: 'POST', body: JSON.stringify(data) }),
    updateCategory: (id, data) => api(`/api/cargos/expenses/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteCategory: id => api(`/api/cargos/expenses/categories/${id}`, { method: 'DELETE' })
};

/* ── Formatters ── */
const fmt = {
    money: n => new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(n || 0),
    number: n => new Intl.NumberFormat('es-CO').format(n || 0),
    date: d => {
        if (!d) return '';
        const [y, m, day] = d.split('-');
        return `${day}/${m}/${y}`;
    }
};

/* ── Notifications ── */
function showAlert(message, type = 'success') {
    const container = document.getElementById('alertContainer');
    const div = document.createElement('div');
    div.className = `alert alert-${type}`;
    div.innerHTML = (type === 'success' ? Icons.check : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>') + `<span>${escapeHtml(message)}</span>`;
    container.appendChild(div);
    setTimeout(() => div.remove(), 5000);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/* ── Init ── */
document.addEventListener('DOMContentLoaded', () => {
    const dateFilter = document.getElementById('dateFilter');
    dateFilter.value = App.currentDate;
    dateFilter.max = getBogotaDateString();

    const today = getBogotaDateString();
    document.getElementById('expenseFilterFrom').value = today;
    document.getElementById('expenseFilterTo').value = today;

    updateTopbarTime();
    setInterval(updateTopbarTime, 1000);

    loadAll();
});

function updateTopbarTime() {
    const now = new Date();
    const opts = { timeZone: 'America/Bogota', weekday: 'short', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' };
    document.getElementById('topbarTime').textContent = now.toLocaleDateString('es-CO', opts);
}

async function loadAll() {
    document.getElementById('stateLoading').style.display = 'block';
    document.getElementById('fleetGrid').innerHTML = '';
    try {
        const [vehicles, loads] = await Promise.all([
            API.getVehicles(),
            API.getLoads(App.currentDate)
        ]);
        App.vehicles = vehicles || [];
        App.loads = loads || [];
        const [drivers, categories] = await Promise.all([API.getDrivers(), API.getCategories()]);
        App.drivers = drivers || [];
        App.categories = categories || [];
        renderFleet();
        renderStats();
        populateExpenseFilterSelects();
        populateDriverSelect('vehicleDriver');
        populateDriverSelect('expenseDriver');
        await Promise.all([loadExpenses(), loadExpensePeriodSummary()]);
    } catch (err) {
        showAlert(err.message || 'Error cargando datos', 'error');
    } finally {
        document.getElementById('stateLoading').style.display = 'none';
    }
}

function changeDate() {
    App.currentDate = document.getElementById('dateFilter').value;
    loadAll();
}

/* ── Render Stats ── */
function renderStats() {
    document.getElementById('statTotalVehicles').textContent = App.vehicles.length;
    const delivered = App.loads.filter(l => l.status === 'ENTREGADO').length;
    const pending = App.loads.filter(l => l.status === 'PENDIENTE').length;
    document.getElementById('statDelivered').textContent = delivered;
    document.getElementById('statPending').textContent = pending;
    const totalMerch = App.loads.reduce((sum, l) => sum + (l.merchandiseValue || 0), 0);
    document.getElementById('statTotalMerchandise').textContent = fmt.money(totalMerch);
}

/* ── Render Fleet ── */
function renderFleet() {
    const grid = document.getElementById('fleetGrid');
    grid.innerHTML = '';

    if (!App.vehicles.length) {
        grid.innerHTML = `<div class="state-loading" style="grid-column:1/-1">No hay carros registrados aún.</div>`;
        return;
    }

    App.vehicles.forEach((vehicle, idx) => {
        const load = App.loads.find(l => l.vehicle && l.vehicle.id === vehicle.id);
        const statusClass = load ? (load.status === 'ENTREGADO' ? 'status-delivered' : 'status-pending') : 'no-load';
        const badgeClass = load ? (load.status === 'ENTREGADO' ? 'delivered' : 'pending') : 'no-load';
        const badgeText = load ? (load.status === 'ENTREGADO' ? 'Entregado' : 'Pendiente') : 'Sin cargue';

        const card = document.createElement('div');
        card.className = `vehicle-card ${statusClass}`;
        card.style.animationDelay = `${idx * 0.06}s`;
        card.innerHTML = `
            <div class="vehicle-header">
                <div class="vehicle-icon">${Icons.truck}</div>
                <span class="status-badge ${badgeClass}">${badgeText}</span>
            </div>
            <div class="vehicle-name">${escapeHtml(vehicle.name || vehicle.plate)}</div>
            <div class="vehicle-plate">${escapeHtml(vehicle.plate)}</div>
            <div class="vehicle-driver">
                <div class="driver-avatar">${vehicle.driver ? initials(vehicle.driver.name) : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>'}</div>
                <div>
                    <div class="driver-name">${vehicle.driver ? escapeHtml(vehicle.driver.name) : 'Sin conductor'}</div>
                    <div class="driver-label">${vehicle.driver ? (vehicle.driver.phone || 'Sin teléfono') : 'Toque para asignar'}</div>
                </div>
            </div>
            <div class="vehicle-merch">
                <div>
                    <div class="merch-label">Mercancía</div>
                    <div class="merch-value">${load ? fmt.money(load.merchandiseValue) : '—'}</div>
                </div>
                <div style="text-align:right">
                    <div class="merch-label">Cierre</div>
                    <div class="merch-settlement">${load && load.settlement ? fmt.money(load.settlement.total) : 'Pendiente'}</div>
                </div>
            </div>
            <div class="vehicle-actions">
                <button type="button" class="btn btn-sm btn-secondary" onclick="openVehicleActions(${vehicle.id})">${Icons.edit} Acciones</button>
                <button type="button" class="btn btn-sm btn-secondary" style="color:var(--rose)" onclick="confirmDeleteVehicle(${vehicle.id}, '${escapeHtml(vehicle.name || vehicle.plate)}')">${Icons.trash}</button>
            </div>
        `;
        grid.appendChild(card);
    });
}

function initials(name) {
    if (!name) return '?';
    return name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}

/* ── Vehicle Actions Modal ── */
function openVehicleActions(vehicleId) {
    const vehicle = App.vehicles.find(v => v.id === vehicleId);
    if (!vehicle) return;
    App.selectedVehicle = vehicle;
    App.selectedLoad = App.loads.find(l => l.vehicle && l.vehicle.id === vehicleId) || null;

    document.getElementById('actionsTitle').textContent = vehicle.name || vehicle.plate;
    document.getElementById('selectedVehiclePreview').innerHTML = `
        <div class="vehicle-preview-big">${Icons.truck}</div>
        <div class="vehicle-preview-name">${escapeHtml(vehicle.name || vehicle.plate)}</div>
        <div class="vehicle-preview-driver">${vehicle.driver ? escapeHtml(vehicle.driver.name) : 'Sin conductor asignado'}</div>
    `;

    // Enable/disable actions based on state
    document.getElementById('actionLoad').style.opacity = App.selectedLoad ? '0.45' : '1';
    document.getElementById('actionSettlement').style.opacity = App.selectedLoad ? '1' : '0.45';

    openModal('modalVehicleActions');
}

function closeVehicleActionsModal() { closeModal('modalVehicleActions'); }

/* ── Load Modal ── */
function actionRegisterLoad() {
    closeVehicleActionsModal();
    if (App.selectedLoad) {
        showAlert('Este carro ya tiene un cargue para la fecha seleccionada', 'error');
        return;
    }
    document.getElementById('loadId').value = '';
    document.getElementById('loadVehicle').value = App.selectedVehicle.id;
    document.getElementById('loadDate').value = App.currentDate;
    document.getElementById('loadMerchandise').value = '';
    document.getElementById('loadNotes').value = '';
    document.getElementById('loadModalTitle').textContent = `Registrar cargue — ${App.selectedVehicle.name || App.selectedVehicle.plate}`;
    document.getElementById('loadVehiclePreview').innerHTML = vehiclePreviewHtml(App.selectedVehicle);
    openModal('modalLoad');
}

function closeLoadModal() { closeModal('modalLoad'); }

async function saveLoad(e) {
    e.preventDefault();
    const data = {
        vehicleId: parseInt(document.getElementById('loadVehicle').value, 10),
        loadDate: document.getElementById('loadDate').value,
        merchandiseValue: parseFloat(document.getElementById('loadMerchandise').value) || 0,
        notes: document.getElementById('loadNotes').value
    };
    if (!data.loadDate || data.merchandiseValue < 0) {
        showAlert('Complete los campos obligatorios', 'error');
        return;
    }
    try {
        await API.createLoad(data);
        showAlert('Cargue registrado correctamente');
        closeLoadModal();
        await loadAll();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

/* ── Settlement Modal ── */
function actionRegisterSettlement() {
    closeVehicleActionsModal();
    if (!App.selectedLoad) {
        showAlert('Primero registre un cargue para este carro', 'error');
        return;
    }
    const load = App.selectedLoad;
    const s = load.settlement || {};
    App.currentSettlementMerchandise = load.merchandiseValue || 0;
    document.getElementById('settlementLoadId').value = load.id;
    document.getElementById('setCash').value = s.cash ? new Intl.NumberFormat('es-CO').format(Math.round(s.cash)) : '';
    document.getElementById('setCoins').value = s.coins ? new Intl.NumberFormat('es-CO').format(Math.round(s.coins)) : '';
    document.getElementById('setQr').value = s.qr ? new Intl.NumberFormat('es-CO').format(Math.round(s.qr)) : '';
    document.getElementById('setReturned').value = s.returnedValue ? new Intl.NumberFormat('es-CO').format(Math.round(s.returnedValue)) : '';
    document.getElementById('setSecurity').value = s.security ? new Intl.NumberFormat('es-CO').format(Math.round(s.security)) : '';
    document.getElementById('settlementVehiclePreview').innerHTML = vehiclePreviewHtml(load.vehicle);

    updateSettlementCalculation();
    openModal('modalSettlement');
}

function updateSettlementCalculation() {
    const merchandise = App.currentSettlementMerchandise || 0;
    const cash = parseFormattedNumber(document.getElementById('setCash').value);
    const coins = parseFormattedNumber(document.getElementById('setCoins').value);
    const qr = parseFormattedNumber(document.getElementById('setQr').value);
    const returned = parseFormattedNumber(document.getElementById('setReturned').value);

    const delivered = cash + coins + qr;
    document.getElementById('setDelivered').value = new Intl.NumberFormat('es-CO').format(delivered);

    const validationEl = document.getElementById('settlementValidation');
    const submitBtn = document.getElementById('settlementSubmitBtn');
    const total = delivered + returned;

    if (Math.abs(total - merchandise) > 0.01) {
        const diff = merchandise - total;
        validationEl.innerHTML = `
            <div class="settlement-validation-info">
                Mercancía: ${fmt.money(merchandise)} · Entregado: ${fmt.money(delivered)} · Devolución: ${fmt.money(returned)} ·
                ${diff > 0 ? `Faltante: ${fmt.money(diff)}` : `Exceso: ${fmt.money(Math.abs(diff))}`}
            </div>`;
        submitBtn.disabled = true;
        submitBtn.classList.add('btn-disabled');
    } else {
        validationEl.innerHTML = `
            <div class="settlement-validation-success">
                Cuadra perfectamente: Entregado ${fmt.money(delivered)} + Devolución ${fmt.money(returned)} = Mercancía ${fmt.money(merchandise)}
            </div>`;
        submitBtn.disabled = false;
        submitBtn.classList.remove('btn-disabled');
    }
}

function closeSettlementModal() { closeModal('modalSettlement'); }

async function saveSettlement(e) {
    e.preventDefault();
    const data = {
        cargoLoadId: parseInt(document.getElementById('settlementLoadId').value, 10),
        deliveredValue: parseFormattedNumber(document.getElementById('setDelivered').value),
        returnedValue: parseFormattedNumber(document.getElementById('setReturned').value),
        cash: parseFormattedNumber(document.getElementById('setCash').value),
        coins: parseFormattedNumber(document.getElementById('setCoins').value),
        qr: parseFormattedNumber(document.getElementById('setQr').value),
        security: parseFormattedNumber(document.getElementById('setSecurity').value),
        expense: parseFormattedNumber(document.getElementById('setExpense').value)
    };

    const merchandise = App.currentSettlementMerchandise || 0;
    const total = data.deliveredValue + data.returnedValue;

    if (Math.abs(total - merchandise) > 0.01) {
        showAlert(`Entregado (${fmt.money(data.deliveredValue)}) + Devolución (${fmt.money(data.returnedValue)}) debe ser igual a la mercancía (${fmt.money(merchandise)})`, 'error');
        return;
    }

    if (data.deliveredValue <= 0) {
        showAlert('El valor entregado debe ser mayor a 0', 'error');
        return;
    }

    try {
        await API.saveSettlement(data);
        await API.markDelivered(data.cargoLoadId);
        showAlert('Cierre de jornada guardado');
        closeSettlementModal();
        await loadAll();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

/* ── Bulk Settlement Modal ── */
function openBulkSettlementModal() {
    const pendingLoads = App.loads.filter(l => !l.settlement && l.status !== 'ENTREGADO');
    if (pendingLoads.length === 0) {
        showAlert('No hay carros pendientes por cerrar', 'info');
        return;
    }
    const totalMerchandise = pendingLoads.reduce((sum, l) => sum + (l.merchandiseValue || 0), 0);
    App.bulkSettlementLoads = pendingLoads;
    App.bulkSettlementMerchandise = totalMerchandise;

    document.getElementById('bulkCash').value = '';
    document.getElementById('bulkCoins').value = '';
    document.getElementById('bulkQr').value = '';
    document.getElementById('bulkReturned').value = '';
    document.getElementById('bulkSecurity').value = '';
    document.getElementById('bulkExpense').value = '';
    document.getElementById('bulkDelivered').value = new Intl.NumberFormat('es-CO').format(0);
    document.getElementById('bulkSettlementInfo').innerHTML = `
        Carros pendientes: <strong>${pendingLoads.length}</strong> ·
        Mercancía total: <strong>${fmt.money(totalMerchandise)}</strong>
    `;
    updateBulkSettlementCalculation();
    openModal('modalBulkSettlement');
}

function closeBulkSettlementModal() { closeModal('modalBulkSettlement'); }

function updateBulkSettlementCalculation() {
    const merchandise = App.bulkSettlementMerchandise || 0;
    const cash = parseFormattedNumber(document.getElementById('bulkCash').value);
    const coins = parseFormattedNumber(document.getElementById('bulkCoins').value);
    const qr = parseFormattedNumber(document.getElementById('bulkQr').value);
    const returned = parseFormattedNumber(document.getElementById('bulkReturned').value);

    const delivered = cash + coins + qr;
    document.getElementById('bulkDelivered').value = new Intl.NumberFormat('es-CO').format(delivered);

    const validationEl = document.getElementById('bulkSettlementValidation');
    const submitBtn = document.getElementById('bulkSettlementSubmitBtn');
    const total = delivered + returned;

    if (Math.abs(total - merchandise) > 0.01) {
        const diff = merchandise - total;
        validationEl.innerHTML = `
            <div class="settlement-validation-info">
                Mercancía total: ${fmt.money(merchandise)} · Entregado: ${fmt.money(delivered)} · Devolución: ${fmt.money(returned)} ·
                ${diff > 0 ? `Faltante: ${fmt.money(diff)}` : `Exceso: ${fmt.money(Math.abs(diff))}`}
            </div>`;
        submitBtn.disabled = true;
        submitBtn.classList.add('btn-disabled');
    } else {
        validationEl.innerHTML = `
            <div class="settlement-validation-success">
                Cuadra perfectamente: ${fmt.money(delivered)} + ${fmt.money(returned)} = ${fmt.money(merchandise)}
            </div>`;
        submitBtn.disabled = false;
        submitBtn.classList.remove('btn-disabled');
    }
}

async function saveBulkSettlement(e) {
    e.preventDefault();
    const data = {
        date: App.currentDate,
        cash: parseFormattedNumber(document.getElementById('bulkCash').value),
        coins: parseFormattedNumber(document.getElementById('bulkCoins').value),
        qr: parseFormattedNumber(document.getElementById('bulkQr').value),
        returnedValue: parseFormattedNumber(document.getElementById('bulkReturned').value),
        security: parseFormattedNumber(document.getElementById('bulkSecurity').value),
        expense: parseFormattedNumber(document.getElementById('bulkExpense').value)
    };

    const merchandise = App.bulkSettlementMerchandise || 0;
    const delivered = data.cash + data.coins + data.qr;
    if (Math.abs((delivered + data.returnedValue) - merchandise) > 0.01) {
        showAlert('Los totales no cuadran con la mercancía total', 'error');
        return;
    }
    if (delivered <= 0) {
        showAlert('Registra al menos un valor de pago', 'error');
        return;
    }

    try {
        await API.saveBulkSettlement(data);
        showAlert('Cierre masivo guardado');
        closeBulkSettlementModal();
        await loadAll();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

/* ── Assign Driver Modal ── */
function actionAssignDriver() {
    closeVehicleActionsModal();
    document.getElementById('assignDriverVehiclePreview').innerHTML = vehiclePreviewHtml(App.selectedVehicle);
    populateDriverSelect('assignDriverSelect', App.selectedVehicle.driver?.id);
    openModal('modalAssignDriver');
}

function closeAssignDriverModal() { closeModal('modalAssignDriver'); }

async function saveAssignDriver() {
    const driverId = document.getElementById('assignDriverSelect').value;
    const data = {
        plate: App.selectedVehicle.plate,
        name: App.selectedVehicle.name,
        driverId: driverId ? parseInt(driverId, 10) : null,
        active: App.selectedVehicle.active
    };
    try {
        await API.updateVehicle(App.selectedVehicle.id, data);
        showAlert('Conductor asignado');
        closeAssignDriverModal();
        await loadAll();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

/* ── Edit Vehicle Modal ── */
function actionEditVehicle() {
    closeVehicleActionsModal();
    openVehicleModal(App.selectedVehicle);
}

function actionDeleteLoad() {
    closeVehicleActionsModal();
    if (!App.selectedLoad) {
        showAlert('No hay cargue seleccionado', 'error');
        return;
    }
    const load = App.selectedLoad;
    const vehicleName = load.vehicle ? load.vehicle.name : 'Este carro';
    openConfirm(`¿Eliminar el cargue de <b>${escapeHtml(vehicleName)}</b> del ${fmt.date(load.loadDate)}? Esta acción no se puede deshacer.`, async () => {
        try {
            await API.deleteLoad(load.id);
            showAlert('Cargue eliminado');
            await loadAll();
        } catch (err) {
            showAlert(err.message, 'error');
        }
    });
}

function openVehicleModal(vehicle = null) {
    document.getElementById('vehicleId').value = vehicle ? vehicle.id : '';
    document.getElementById('vehiclePlate').value = vehicle ? vehicle.plate : '';
    document.getElementById('vehicleName').value = vehicle ? vehicle.name : '';
    populateDriverSelect('vehicleDriver', vehicle?.driver?.id);
    openModal('modalVehicle');
}

function closeVehicleModal() { closeModal('modalVehicle'); }

async function saveVehicle(e) {
    e.preventDefault();
    const id = document.getElementById('vehicleId').value;
    const data = {
        plate: document.getElementById('vehiclePlate').value.trim(),
        name: document.getElementById('vehicleName').value.trim(),
        driverId: document.getElementById('vehicleDriver').value ? parseInt(document.getElementById('vehicleDriver').value, 10) : null,
        active: true
    };
    if (!data.plate || !data.name) {
        showAlert('Placa y nombre son obligatorios', 'error');
        return;
    }
    try {
        if (id) await API.updateVehicle(parseInt(id, 10), data);
        else await API.createVehicle(data);
        showAlert(id ? 'Carro actualizado' : 'Carro creado');
        closeVehicleModal();
        await loadAll();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

function confirmDeleteVehicle(id, name) {
    openConfirm(`¿Eliminar el carro <b>${escapeHtml(name)}</b>? Si tiene cargues registrados, se desactivará en lugar de eliminarse para conservar el historial.`, async () => {
        try {
            const res = await API.deleteVehicle(id);
            showAlert(res?.message || 'Carro eliminado');
            await loadAll();
        } catch (err) {
            showAlert(err.message, 'error');
        }
    });
}

/* ── Drivers Management Modal ── */
async function openDriversModal() {
    resetDriverForm();
    openModal('modalDrivers');
    try {
        App.drivers = await API.getDrivers();
        renderDriversList();
        populateDriverSelect('vehicleDriver');
        populateDriverSelect('expenseDriver');
    } catch (err) {
        showAlert(err.message || 'Error cargando conductores', 'error');
    }
}

function closeDriversModal() { closeModal('modalDrivers'); }

function resetDriverForm() {
    document.getElementById('driverFormTitle').textContent = 'Nuevo conductor';
    document.getElementById('driverId').value = '';
    document.getElementById('driverName').value = '';
    document.getElementById('driverPhone').value = '';
}

function editDriver(driver) {
    document.getElementById('driverFormTitle').textContent = 'Editar conductor';
    document.getElementById('driverId').value = driver.id;
    document.getElementById('driverName').value = driver.name || '';
    document.getElementById('driverPhone').value = driver.phone || '';
}

function confirmDeleteDriver(id, name) {
    openConfirm(`¿Eliminar al conductor <b>${escapeHtml(name)}</b>? Si tiene carros asignados, se desactivará para conservar el historial.`, async () => {
        try {
            const res = await API.deleteDriver(id);
            showAlert(res?.message || 'Conductor eliminado');
            App.drivers = await API.getDrivers();
            renderDriversList();
            populateDriverSelect('vehicleDriver');
            populateDriverSelect('expenseDriver');
        } catch (err) {
            showAlert(err.message, 'error');
        }
    });
}

function renderDriversList() {
    const list = document.getElementById('driversList');
    if (!App.drivers || App.drivers.length === 0) {
        list.innerHTML = '<div class="categories-empty">No hay conductores registrados</div>';
        return;
    }
    list.innerHTML = App.drivers.map(d => `
        <div class="category-item">
            <div class="category-info">
                <div class="category-name">${escapeHtml(d.name)} ${d.phone ? `<span class="category-meta">— ${escapeHtml(d.phone)}</span>` : ''}</div>
                ${!d.active ? '<span class="category-meta">Desactivado</span>' : ''}
            </div>
            <div class="category-actions">
                <button type="button" class="icon-btn" onclick='editDriver(${JSON.stringify(d)})' title="Editar">${Icons.edit}</button>
                <button type="button" class="icon-btn danger" onclick="confirmDeleteDriver(${d.id}, '${escapeHtml(d.name).replace(/'/g, "\\'")}')" title="Eliminar">${Icons.trash}</button>
            </div>
        </div>
    `).join('');
}

async function saveDriver(e) {
    e.preventDefault();
    const id = document.getElementById('driverId').value;
    const data = {
        name: document.getElementById('driverName').value.trim(),
        phone: document.getElementById('driverPhone').value.trim(),
        active: true
    };
    if (!data.name) {
        showAlert('El nombre es obligatorio', 'error');
        return;
    }
    try {
        if (id) await API.updateDriver(parseInt(id, 10), data);
        else await API.createDriver(data);
        showAlert(id ? 'Conductor actualizado' : 'Conductor creado');
        resetDriverForm();
        App.drivers = await API.getDrivers();
        renderDriversList();
        populateDriverSelect('vehicleDriver');
        populateDriverSelect('expenseDriver');
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

function populateDriverSelect(selectId, selectedId = null) {
    const sel = document.getElementById(selectId);
    sel.innerHTML = '<option value="">— Sin conductor —</option>';
    (App.drivers || []).forEach(d => {
        const opt = document.createElement('option');
        opt.value = d.id;
        opt.textContent = d.name + (d.phone ? ` — ${d.phone}` : '');
        if (selectedId && d.id === selectedId) opt.selected = true;
        sel.appendChild(opt);
    });
}

/* ── Helpers ── */
function vehiclePreviewHtml(vehicle) {
    return `
        <div class="vehicle-preview-form-icon">${Icons.truck}</div>
        <div>
            <div class="vehicle-preview-form-name">${escapeHtml(vehicle.name || vehicle.plate)}</div>
            <div class="vehicle-preview-form-plate">${escapeHtml(vehicle.plate)}</div>
        </div>
    `;
}

/* ── Delete / Confirm Modal ── */
function openConfirm(message, handler) {
    document.getElementById('deleteMessage').innerHTML = message;
    App.deleteHandler = handler;
    openModal('modalDelete');
}

function closeDeleteModal() {
    closeModal('modalDelete');
    App.deleteHandler = null;
}

function confirmDeleteAction() {
    if (App.deleteHandler) App.deleteHandler();
    closeDeleteModal();
}

/* ── Report ── */
async function sendReportManually() {
    try {
        const res = await API.sendReport(App.currentDate);
        showAlert(res?.message || 'Reporte enviado');
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

async function generateReport() {
    try {
        const report = await API.getReport(App.currentDate);
        document.getElementById('reportCardContainer').classList.remove('hidden');
        document.getElementById('reportDate').textContent = fmt.date(report.date || App.currentDate);

        let expensesByCategoryHtml = '';
        if (report.expensesByCategory && Object.keys(report.expensesByCategory).length > 0) {
            expensesByCategoryHtml = Object.entries(report.expensesByCategory)
                .map(([name, amount]) => `
                    <div class="report-item">
                        <div class="report-label">${escapeHtml(name)}</div>
                        <div class="report-value">${fmt.money(amount)}</div>
                    </div>
                `).join('');
        }

        document.getElementById('reportBody').innerHTML = `
            <div class="report-item"><div class="report-label">Carros con cargue</div><div class="report-value">${report.loads?.length || 0}</div></div>
            <div class="report-item"><div class="report-label">Entregados</div><div class="report-value">${report.deliveredCount || 0}</div></div>
            <div class="report-item"><div class="report-label">Pendientes</div><div class="report-value pending">${report.pendingCount || 0}</div></div>
            <div class="report-item"><div class="report-label">Mercancía total</div><div class="report-value">${fmt.money(report.totalMerchandise)}</div></div>
            <div class="report-item"><div class="report-label">Total entregado</div><div class="report-value">${fmt.money(report.totalDelivered)}</div></div>
            <div class="report-item"><div class="report-label">Devoluciones</div><div class="report-value returned">${fmt.money(report.totalReturned)}</div></div>
            <div class="report-item"><div class="report-label">Efectivo</div><div class="report-value">${fmt.money(report.totalCash)}</div></div>
            <div class="report-item"><div class="report-label">Monedas</div><div class="report-value">${fmt.money(report.totalCoins)}</div></div>
            <div class="report-item"><div class="report-label">QR</div><div class="report-value">${fmt.money(report.totalQr)}</div></div>
            <div class="report-item"><div class="report-label">Seguridad</div><div class="report-value">${fmt.money(report.totalSecurity)}</div></div>
            <div class="report-item"><div class="report-label">Gastos de los carros</div><div class="report-value">${fmt.money(report.totalExpense)}</div></div>
            <div class="report-item"><div class="report-label">Total gastos empresa</div><div class="report-value">${fmt.money(report.totalExpenses)}</div></div>
            ${expensesByCategoryHtml}
            <div class="report-item" style="grid-column:1/-1"><div class="report-label">Gran total</div><div class="report-value total">${fmt.money(report.grandTotal)}</div></div>
        `;
        document.getElementById('reportCardContainer').scrollIntoView({ behavior: 'smooth' });
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

/* ── Company Expenses ── */
function populateExpenseFilterSelects() {
    // categories
    const catSel = document.getElementById('expenseFilterCategory');
    const currentCat = catSel.value;
    catSel.innerHTML = '<option value="">Todas</option>';
    App.categories.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id; opt.textContent = c.name;
        if (String(c.id) === currentCat) opt.selected = true;
        catSel.appendChild(opt);
    });

    // vehicles
    const vehSel = document.getElementById('expenseFilterVehicle');
    const currentVeh = vehSel.value;
    vehSel.innerHTML = '<option value="">Todos</option>';
    App.vehicles.forEach(v => {
        const opt = document.createElement('option');
        opt.value = v.id; opt.textContent = `${v.name} — ${v.plate}`;
        if (String(v.id) === currentVeh) opt.selected = true;
        vehSel.appendChild(opt);
    });

    // drivers
    const drvSel = document.getElementById('expenseFilterDriver');
    const currentDrv = drvSel.value;
    drvSel.innerHTML = '<option value="">Todos</option>';
    App.drivers.forEach(d => {
        const opt = document.createElement('option');
        opt.value = d.id; opt.textContent = d.name;
        if (String(d.id) === currentDrv) opt.selected = true;
        drvSel.appendChild(opt);
    });
}

function getExpenseFilterParams() {
    const params = new URLSearchParams();
    const from = document.getElementById('expenseFilterFrom').value;
    const to = document.getElementById('expenseFilterTo').value;
    const categoryId = document.getElementById('expenseFilterCategory').value;
    const vehicleId = document.getElementById('expenseFilterVehicle').value;
    const driverId = document.getElementById('expenseFilterDriver').value;
    const hasAttachment = document.getElementById('expenseFilterAttachment').value;

    if (from) params.append('dateFrom', from);
    if (to) params.append('dateTo', to);
    if (categoryId) params.append('categoryId', categoryId);
    if (vehicleId) params.append('vehicleId', vehicleId);
    if (driverId) params.append('driverId', driverId);
    if (hasAttachment) params.append('hasAttachment', hasAttachment);
    return params.toString();
}

async function loadExpenses() {
    document.getElementById('expenseLoading').style.display = 'block';
    document.getElementById('expenseTableWrap').classList.add('hidden');
    document.getElementById('expenseEmpty').classList.add('hidden');
    try {
        App.expenses = await API.getExpenses(getExpenseFilterParams());
        App.expensePage = 1;
        renderExpenses();
        await loadExpensePeriodSummary();
    } catch (err) {
        showAlert(err.message || 'Error cargando gastos', 'error');
    } finally {
        document.getElementById('expenseLoading').style.display = 'none';
    }
}

function renderExpenses() {
    const tbody = document.getElementById('expenseTableBody');
    tbody.innerHTML = '';

    if (!App.expenses.length) {
        document.getElementById('expenseEmpty').classList.remove('hidden');
        document.getElementById('expenseTableWrap').classList.add('hidden');
        document.getElementById('expenseSummary').innerHTML = '';
        document.getElementById('expensePaginationWrap').classList.add('hidden');
        return;
    }

    document.getElementById('expenseTableWrap').classList.remove('hidden');
    document.getElementById('expenseEmpty').classList.add('hidden');

    const total = App.expenses.reduce((s, e) => s + (e.amount || 0), 0);
    const totalPages = Math.ceil(App.expenses.length / App.expensePageSize);
    if (App.expensePage > totalPages) App.expensePage = totalPages || 1;

    const start = (App.expensePage - 1) * App.expensePageSize;
    const end = start + App.expensePageSize;
    const pageItems = App.expenses.slice(start, end);

    pageItems.forEach((exp, idx) => {
        const tr = document.createElement('tr');
        tr.style.animationDelay = `${idx * 0.03}s`;
        tr.innerHTML = `
            <td>${fmt.date(exp.expenseDate)}</td>
            <td><span class="expense-category-badge" style="background:${hexToRgba(exp.category?.color, 0.1)};color:${exp.category?.color || 'var(--gray-500)'}">${escapeHtml(exp.category?.name || '—')}</span></td>
            <td>${escapeHtml(exp.description || '—')}</td>
            <td>
                <div class="expense-relations">
                    ${exp.vehicle ? `<span class="expense-rel">${escapeHtml(exp.vehicle.name)}</span>` : ''}
                    ${exp.driver ? `<span class="expense-rel">${escapeHtml(exp.driver.name)}</span>` : ''}
                    ${!exp.vehicle && !exp.driver ? '<span class="expense-rel empty">General</span>' : ''}
                </div>
            </td>
            <td class="num">${fmt.money(exp.amount)}</td>
            <td>
                ${exp.hasAttachment
                    ? `<a href="${API.downloadExpenseAttachment(exp.id)}" target="_blank" class="attachment-link"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> ${escapeHtml(exp.attachmentOriginalName || 'Adjunto')}</a>`
                    : '<span class="no-attachment">—</span>'}
            </td>
            <td>
                <div class="expense-actions">
                    <button class="btn btn-sm btn-secondary" onclick="openExpenseModal(${exp.id})">${Icons.edit}</button>
                    <button class="btn btn-sm btn-danger" onclick="confirmDeleteExpense(${exp.id})">${Icons.trash}</button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });

    document.getElementById('expenseSummary').innerHTML = `
        <div class="expense-summary-card">
            <span class="expense-summary-label">Total gastos filtrados</span>
            <span class="expense-summary-value">${fmt.money(total)}</span>
        </div>
    `;

    renderExpensePagination(totalPages, App.expenses.length);
}

function renderExpensePagination(totalPages, totalItems) {
    const wrap = document.getElementById('expensePaginationWrap');
    const info = document.getElementById('expensePaginationInfo');
    const controls = document.getElementById('expensePaginationControls');

    if (totalPages <= 1) {
        wrap.classList.add('hidden');
        return;
    }

    wrap.classList.remove('hidden');
    const start = (App.expensePage - 1) * App.expensePageSize + 1;
    const end = Math.min(start + App.expensePageSize - 1, totalItems);
    info.textContent = `${start}–${end} de ${totalItems} gastos`;

    controls.innerHTML = '';
    const prevBtn = document.createElement('button');
    prevBtn.className = 'btn btn-sm btn-secondary';
    prevBtn.textContent = 'Anterior';
    prevBtn.disabled = App.expensePage === 1;
    prevBtn.onclick = () => changeExpensePage(App.expensePage - 1);
    controls.appendChild(prevBtn);

    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.className = 'btn btn-sm ' + (i === App.expensePage ? 'btn-primary' : 'btn-secondary');
        btn.textContent = i;
        btn.onclick = () => changeExpensePage(i);
        controls.appendChild(btn);
    }

    const nextBtn = document.createElement('button');
    nextBtn.className = 'btn btn-sm btn-secondary';
    nextBtn.textContent = 'Siguiente';
    nextBtn.disabled = App.expensePage === totalPages;
    nextBtn.onclick = () => changeExpensePage(App.expensePage + 1);
    controls.appendChild(nextBtn);
}

function changeExpensePage(page) {
    App.expensePage = page;
    renderExpenses();
}

function openExpenseModal(expenseId = null) {
    App.selectedExpense = expenseId ? App.expenses.find(e => e.id === expenseId) : null;
    const isEdit = !!App.selectedExpense;

    document.getElementById('expenseModalTitle').textContent = isEdit ? 'Editar gasto' : 'Registrar gasto';
    document.getElementById('expenseId').value = isEdit ? App.selectedExpense.id : '';
    document.getElementById('expenseDate').value = isEdit ? App.selectedExpense.expenseDate : getBogotaDateString();
    document.getElementById('expenseDescription').value = isEdit ? App.selectedExpense.description || '' : '';
    document.getElementById('expenseAmount').value = isEdit ? App.selectedExpense.amount : '';

    populateCategorySelect('expenseCategory', isEdit ? App.selectedExpense.category?.id : '');
    populateVehicleSelect('expenseVehicle', isEdit ? App.selectedExpense.vehicle?.id : '');
    populateDriverSelect('expenseDriver', isEdit ? App.selectedExpense.driver?.id : '');

    const fileInput = document.getElementById('expenseAttachment');
    fileInput.value = '';
    const info = document.getElementById('expenseAttachmentInfo');
    if (isEdit && App.selectedExpense.hasAttachment) {
        info.innerHTML = `Archivo actual: <b>${escapeHtml(App.selectedExpense.attachmentOriginalName || 'adjunto')}</b>. Sube uno nuevo para reemplazarlo.`;
        info.classList.remove('hidden');
    } else {
        info.classList.add('hidden');
    }

    openModal('modalExpense');
}

function closeExpenseModal() { closeModal('modalExpense'); }

async function saveExpense(e) {
    e.preventDefault();
    const id = document.getElementById('expenseId').value;
    const categoryId = document.getElementById('expenseCategory').value;
    const amount = document.getElementById('expenseAmount').value;

    if (!categoryId) {
        showAlert('Selecciona una categoría', 'error');
        return;
    }
    if (!amount || isNaN(amount) || Number(amount) < 0) {
        showAlert('Ingresa un monto válido', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('expenseDate', document.getElementById('expenseDate').value);
    formData.append('categoryId', categoryId);
    formData.append('amount', amount);
    formData.append('description', document.getElementById('expenseDescription').value);

    const vehicleId = document.getElementById('expenseVehicle').value;
    const driverId = document.getElementById('expenseDriver').value;
    if (vehicleId) formData.append('vehicleId', vehicleId);
    if (driverId) formData.append('driverId', driverId);

    const file = document.getElementById('expenseAttachment').files[0];
    if (file) formData.append('attachment', file);

    try {
        if (id) await API.updateExpense(parseInt(id, 10), formData);
        else await API.createExpense(formData);
        showAlert(id ? 'Gasto actualizado' : 'Gasto registrado');
        closeExpenseModal();
        await loadExpenses();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

function confirmDeleteExpense(id) {
    openConfirm('¿Eliminar este gasto? El soporte adjunto también se eliminará.', async () => {
        try {
            await API.deleteExpense(id);
            showAlert('Gasto eliminado');
            await loadExpenses();
        } catch (err) {
            showAlert(err.message, 'error');
        }
    });
}

function populateCategorySelect(selectId, selected = '') {
    const sel = document.getElementById(selectId);
    sel.innerHTML = '<option value="">Seleccione…</option>';
    App.categories.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id; opt.textContent = c.name;
        if (String(c.id) === String(selected)) opt.selected = true;
        sel.appendChild(opt);
    });
}

function hexToRgba(hex, alpha) {
    if (!hex || !hex.startsWith('#')) return `rgba(100,116,139,${alpha})`;
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r},${g},${b},${alpha})`;
}

function populateVehicleSelect(selectId, selected = '') {
    const sel = document.getElementById(selectId);
    sel.innerHTML = '<option value="">— Ninguno —</option>';
    App.vehicles.forEach(v => {
        const opt = document.createElement('option');
        opt.value = v.id; opt.textContent = `${v.name} — ${v.plate}`;
        if (selected && v.id === selected) opt.selected = true;
        sel.appendChild(opt);
    });
}

/* ── Expense Period Summary ── */
function setExpensePeriod(period) {
    const today = getBogotaDateString();
    let from = today;
    let to = today;

    if (period === 'week') {
        from = getBogotaStartOfWeek();
    } else if (period === 'month') {
        from = getBogotaStartOfMonth();
    }

    document.getElementById('expenseFilterFrom').value = from;
    document.getElementById('expenseFilterTo').value = to;
    loadExpenses();
    loadExpensePeriodSummary();
}

async function loadExpensePeriodSummary() {
    const from = document.getElementById('expenseFilterFrom').value;
    const to = document.getElementById('expenseFilterTo').value;
    if (!from || !to) return;

    try {
        const summary = await API.getExpenseSummary(`dateFrom=${from}&dateTo=${to}`);
        document.getElementById('expensePeriodTotal').textContent = fmt.money(summary.total);

        const categoriesEl = document.getElementById('expensePeriodCategories');
        if (summary.expensesByCategory && Object.keys(summary.expensesByCategory).length > 0) {
            categoriesEl.innerHTML = Object.entries(summary.expensesByCategory)
                .map(([name, amount]) => `
                    <div class="expense-period-category">
                        <span class="expense-period-cat-name">${escapeHtml(name)}</span>
                        <span class="expense-period-cat-value">${fmt.money(amount)}</span>
                    </div>
                `).join('');
        } else {
            categoriesEl.innerHTML = '<div class="expense-period-empty">No hay gastos en este período</div>';
        }
    } catch (err) {
        console.error('Error cargando resumen de gastos:', err);
    }
}

/* ── Expense Categories Admin ── */
function openCategoriesModal() {
    renderCategoriesList();
    openModal('modalCategories');
}

function closeCategoriesModal() { closeModal('modalCategories'); }

function renderCategoriesList() {
    const list = document.getElementById('categoriesList');
    list.innerHTML = '';
    if (!App.categories.length) {
        list.innerHTML = '<div class="state-loading">No hay categorías registradas.</div>';
        return;
    }
    App.categories.forEach(c => {
        const item = document.createElement('div');
        item.className = 'category-item';
        item.innerHTML = `
            <div class="category-info">
                <span class="category-dot" style="background:${c.color || 'var(--gray-400)'}"></span>
                <span class="category-name">${escapeHtml(c.name)}</span>
            </div>
            <div class="category-actions">
                <button class="btn btn-sm btn-secondary" onclick="editCategory(${c.id})">Editar</button>
                <button class="btn btn-sm btn-danger" onclick="confirmDeleteCategory(${c.id}, '${escapeHtml(c.name)}')">Eliminar</button>
            </div>
        `;
        list.appendChild(item);
    });
}

function editCategory(id) {
    const category = App.categories.find(c => c.id === id);
    if (!category) return;
    document.getElementById('categoryId').value = category.id;
    document.getElementById('categoryName').value = category.name;
    document.getElementById('categoryColor').value = category.color || '#64748B';
    document.getElementById('categoryFormTitle').textContent = 'Editar categoría';
}

function resetCategoryForm() {
    document.getElementById('categoryId').value = '';
    document.getElementById('categoryName').value = '';
    document.getElementById('categoryColor').value = '#64748B';
    document.getElementById('categoryFormTitle').textContent = 'Nueva categoría';
}

async function saveCategory(e) {
    e.preventDefault();
    const id = document.getElementById('categoryId').value;
    const data = {
        name: document.getElementById('categoryName').value.trim(),
        color: document.getElementById('categoryColor').value,
        active: true
    };
    if (!data.name) {
        showAlert('El nombre es obligatorio', 'error');
        return;
    }
    try {
        if (id) await API.updateCategory(parseInt(id, 10), data);
        else await API.createCategory(data);
        showAlert(id ? 'Categoría actualizada' : 'Categoría creada');
        App.categories = await API.getCategories();
        renderCategoriesList();
        populateExpenseFilterSelects();
        resetCategoryForm();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

function confirmDeleteCategory(id, name) {
    openConfirm(`¿Eliminar la categoría <b>${escapeHtml(name)}</b>? Solo se puede eliminar si no tiene gastos asociados.`, async () => {
        try {
            await API.deleteCategory(id);
            showAlert('Categoría eliminada');
            App.categories = await API.getCategories();
            renderCategoriesList();
            populateExpenseFilterSelects();
        } catch (err) {
            showAlert(err.message, 'error');
        }
    });
}

/* ── Modal Utilities ── */
function openModal(id) {
    const modal = document.getElementById(id);
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeModal(id) {
    const modal = document.getElementById(id);
    modal.classList.remove('active');
    document.body.style.overflow = '';
}

// Close on backdrop click
document.addEventListener('click', e => {
    if (e.target.classList.contains('modal-backdrop')) {
        closeModal(e.target.id);
    }
});

// Escape key
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal-backdrop.active').forEach(m => closeModal(m.id));
    }
});

/* ── Sidebar ── */
function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('open');
}

// Click outside to close sidebar on mobile
document.addEventListener('click', e => {
    const sidebar = document.getElementById('sidebar');
    const toggle = document.querySelector('.menu-toggle');
    if (window.innerWidth <= 900 && sidebar.classList.contains('open') &&
        !sidebar.contains(e.target) && !toggle.contains(e.target)) {
        sidebar.classList.remove('open');
    }
});
