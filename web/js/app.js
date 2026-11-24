// ========================================================
// E-Zorax: Front-End Core Application Logic
// Single source of truth is always the Java REST API
// ========================================================

const API_BASE = '/api';

// Currency Formatter with Indian Digit Grouping (Rs. 47,200.00)
function formatCurrency(val) {
    if (val === null || val === undefined || isNaN(val)) return 'Rs. 0.00';
    return 'Rs. ' + Number(val).toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

// Format date nicely
function formatDate(dateStr) {
    if (!dateStr) return '—';
    return dateStr;
}

// Toast Notification Manager
function showToast(message, type = 'info') {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    let iconSvg = '';
    if (type === 'success') {
        iconSvg = `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>`;
    } else if (type === 'error') {
        iconSvg = `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>`;
    } else {
        iconSvg = `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>`;
    }

    toast.innerHTML = `${iconSvg}<span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(20px)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// Modal Helpers
function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.add('open');
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('open');
}

// Generic API Client
async function apiCall(endpoint, method = 'GET', body = null) {
    const options = {
        method,
        headers: {
            'Accept': 'application/json'
        }
    };
    if (body) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    try {
        const res = await fetch(`${API_BASE}${endpoint}`, options);
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            const err = data.error || data.message || `Request failed with status ${res.status}`;
            throw new Error(err);
        }
        return data;
    } catch (err) {
        console.error(`API Error [${method} ${endpoint}]:`, err);
        throw err;
    }
}

// HTML Escape
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Pill Badge Generator
function getBadge(type) {
    const t = (type || '').toUpperCase();
    if (t === 'FULL_TIME') return `<span class="badge badge-fulltime">Full-Time</span>`;
    if (t === 'PART_TIME') return `<span class="badge badge-parttime">Part-Time</span>`;
    if (t === 'CONTRACT') return `<span class="badge badge-contract">Contract</span>`;
    return `<span class="badge badge-neutral">${escapeHtml(type)}</span>`;
}

// -------------------------------------------------------------
// 1. HOME PAGE LOGIC
// -------------------------------------------------------------
async function initHomePage() {
    try {
        const summary = await apiCall('/summary');
        
        const countEl = document.getElementById('statTotalEmployees');
        if (countEl) countEl.innerText = summary.employeeCount ?? 0;

        const payoutEl = document.getElementById('statTotalPayout');
        if (payoutEl) payoutEl.innerText = formatCurrency(summary.totalPayout);

        const highestEl = document.getElementById('statHighestPaid');
        if (highestEl) {
            if (summary.highestPaid) {
                highestEl.innerText = `${summary.highestPaid.name}`;
                const subEl = document.getElementById('statHighestSub');
                if (subEl) subEl.innerText = formatCurrency(summary.highestPaid.salary);
            } else {
                highestEl.innerText = 'None';
            }
        }

        const deptEl = document.getElementById('statDepartments');
        if (deptEl) deptEl.innerText = summary.departmentCount ?? 3;

    } catch (err) {
        showToast('Failed to load dashboard metrics: ' + err.message, 'error');
    }
}

// -------------------------------------------------------------
// 2. EMPLOYEES PAGE LOGIC
// -------------------------------------------------------------
let allDepartments = [];
let editingEmpId = null;

async function initEmployeesPage() {
    await loadDepartmentsFilter();
    await loadEmployees();

    // Event listeners
    const searchInput = document.getElementById('searchInput');
    const typeFilter = document.getElementById('typeFilter');
    const deptFilter = document.getElementById('deptFilter');

    if (searchInput) searchInput.addEventListener('input', debounce(loadEmployees, 300));
    if (typeFilter) typeFilter.addEventListener('change', loadEmployees);
    if (deptFilter) deptFilter.addEventListener('change', loadEmployees);

    const empTypeSelect = document.getElementById('modalEmpType');
    if (empTypeSelect) empTypeSelect.addEventListener('change', updateModalFields);
}

function debounce(func, wait) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}

async function loadDepartmentsFilter() {
    try {
        allDepartments = await apiCall('/departments');
        const deptFilter = document.getElementById('deptFilter');
        const modalDept = document.getElementById('modalDept');

        if (deptFilter) {
            deptFilter.innerHTML = '<option value="">All Departments</option>' +
                allDepartments.map(d => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join('');
        }
        if (modalDept) {
            modalDept.innerHTML = allDepartments.map(d => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join('');
        }
    } catch (err) {
        console.error('Failed to load departments:', err);
    }
}

async function loadEmployees() {
    const tbody = document.getElementById('employeesTableBody');
    if (!tbody) return;

    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:2rem; color:var(--text-muted);">Loading employee data from database...</td></tr>`;

    const search = document.getElementById('searchInput')?.value || '';
    const type = document.getElementById('typeFilter')?.value || '';
    const dept = document.getElementById('deptFilter')?.value || '';

    const params = new URLSearchParams();
    if (search.trim()) params.append('search', search.trim());
    if (type.trim()) params.append('type', type.trim());
    if (dept.trim()) params.append('dept', dept.trim());

    try {
        const list = await apiCall(`/employees?${params.toString()}`);
        if (!list || list.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:2.5rem; color:var(--text-muted);">No employees found matching the filter criteria.</td></tr>`;
            return;
        }

        tbody.innerHTML = list.map(emp => `
            <tr>
                <td><strong>${escapeHtml(emp.id)}</strong></td>
                <td>${escapeHtml(emp.name)}</td>
                <td>${escapeHtml(emp.deptName || 'Dept ' + emp.deptId)}</td>
                <td>${getBadge(emp.type)}</td>
                <td class="tabular font-semibold" style="font-weight:600; color:var(--navy);">${formatCurrency(emp.netSalary)}</td>
                <td style="font-size:0.82rem; color:var(--text-muted);">${formatDate(emp.updatedAt)}</td>
                <td>
                    <div style="display:flex; gap:0.4rem;">
                        <button class="btn btn-secondary btn-sm" onclick="viewPayslip('${emp.id}')" title="View Payslip">
                            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="14" height="14"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
                            Payslip
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="openEditEmployee('${emp.id}')" title="Edit Employee">
                            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="14" height="14"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
                        </button>
                        <button class="btn btn-danger btn-sm" onclick="confirmDelete('${emp.id}', '${escapeHtml(emp.name)}')" title="Delete Employee">
                            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" width="14" height="14"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:2rem; color:var(--error);">Error loading employees: ${escapeHtml(err.message)}</td></tr>`;
        showToast('Error loading employees: ' + err.message, 'error');
    }
}

// Modal Fields Visibility Switcher
function updateModalFields() {
    const type = document.getElementById('modalEmpType')?.value || 'FULL_TIME';
    const ftFields = document.getElementById('fullTimeFields');
    const ptFields = document.getElementById('partTimeFields');
    const ctFields = document.getElementById('contractFields');

    if (ftFields) ftFields.style.display = type === 'FULL_TIME' ? 'block' : 'none';
    if (ptFields) ptFields.style.display = type === 'PART_TIME' ? 'block' : 'none';
    if (ctFields) ctFields.style.display = type === 'CONTRACT' ? 'block' : 'none';
}

function openAddEmployee() {
    editingEmpId = null;
    document.getElementById('modalTitle').innerText = 'Add New Employee';
    document.getElementById('modalEmpId').value = '';
    document.getElementById('modalEmpId').readOnly = false;
    document.getElementById('modalEmpName').value = '';
    document.getElementById('modalEmpType').value = 'FULL_TIME';
    document.getElementById('modalEmpType').disabled = false;
    document.getElementById('modalBasic').value = '';
    document.getElementById('modalHours').value = '';
    document.getElementById('modalRate').value = '';
    document.getElementById('modalFee').value = '';
    document.getElementById('modalProjects').value = '';
    document.getElementById('modalError').style.display = 'none';

    updateModalFields();
    openModal('employeeModal');
}

async function openEditEmployee(id) {
    editingEmpId = id;
    try {
        const emp = await apiCall(`/employees/${id}`);
        document.getElementById('modalTitle').innerText = `Edit Employee (${emp.id})`;
        document.getElementById('modalEmpId').value = emp.id;
        document.getElementById('modalEmpId').readOnly = true;
        document.getElementById('modalEmpName').value = emp.name;
        document.getElementById('modalDept').value = emp.deptId;
        document.getElementById('modalEmpType').value = emp.type;
        document.getElementById('modalEmpType').disabled = true; // type immutable on edit

        if (emp.type === 'FULL_TIME') {
            document.getElementById('modalBasic').value = emp.basic || '';
        } else if (emp.type === 'PART_TIME') {
            document.getElementById('modalHours').value = emp.hoursWorked || '';
            document.getElementById('modalRate').value = emp.hourlyRate || '';
        } else if (emp.type === 'CONTRACT') {
            document.getElementById('modalFee').value = emp.feePerProject || '';
            document.getElementById('modalProjects').value = emp.projectsDone || '';
        }

        document.getElementById('modalError').style.display = 'none';
        updateModalFields();
        openModal('employeeModal');
    } catch (err) {
        showToast('Failed to load employee details: ' + err.message, 'error');
    }
}

async function saveEmployee() {
    const errorBox = document.getElementById('modalError');
    errorBox.style.display = 'none';

    const id = document.getElementById('modalEmpId').value.trim();
    const name = document.getElementById('modalEmpName').value.trim();
    const deptId = parseInt(document.getElementById('modalDept').value);
    const type = document.getElementById('modalEmpType').value;

    if (!id && !editingEmpId) {
        showModalError('Employee ID cannot be empty');
        return;
    }
    if (!name) {
        showModalError('Employee Name cannot be empty');
        return;
    }

    const payload = { id, name, deptId, type };

    if (type === 'FULL_TIME') {
        const basic = parseFloat(document.getElementById('modalBasic').value);
        if (isNaN(basic) || basic <= 0) {
            showModalError('Basic salary must be a positive number');
            return;
        }
        payload.basic = basic;
    } else if (type === 'PART_TIME') {
        const hours = parseInt(document.getElementById('modalHours').value);
        const rate = parseFloat(document.getElementById('modalRate').value);
        if (isNaN(hours) || hours < 0 || isNaN(rate) || rate <= 0) {
            showModalError('Hours worked (>= 0) and Hourly rate (> 0) must be valid');
            return;
        }
        payload.hoursWorked = hours;
        payload.hourlyRate = rate;
    } else if (type === 'CONTRACT') {
        const fee = parseFloat(document.getElementById('modalFee').value);
        const projects = parseInt(document.getElementById('modalProjects').value);
        if (isNaN(fee) || fee <= 0 || isNaN(projects) || projects < 0) {
            showModalError('Fee per project (> 0) and Projects done (>= 0) must be valid');
            return;
        }
        payload.feePerProject = fee;
        payload.projectsDone = projects;
    }

    try {
        if (editingEmpId) {
            await apiCall(`/employees/${editingEmpId}`, 'PUT', payload);
            showToast('Employee updated successfully in MySQL!', 'success');
        } else {
            await apiCall('/employees', 'POST', payload);
            showToast('Employee added successfully to MySQL!', 'success');
        }
        closeModal('employeeModal');
        await loadEmployees();
    } catch (err) {
        showModalError(err.message);
    }
}

function showModalError(msg) {
    const errorBox = document.getElementById('modalError');
    if (errorBox) {
        errorBox.innerText = msg;
        errorBox.style.display = 'block';
    } else {
        showToast(msg, 'error');
    }
}

let deleteTargetId = null;
function confirmDelete(id, name) {
    deleteTargetId = id;
    document.getElementById('deleteTargetName').innerText = `${name} (${id})`;
    openModal('deleteModal');
}

async function executeDelete() {
    if (!deleteTargetId) return;
    try {
        await apiCall(`/employees/${deleteTargetId}`, 'DELETE');
        showToast(`Employee ${deleteTargetId} deleted from database.`, 'success');
        closeModal('deleteModal');
        deleteTargetId = null;
        await loadEmployees();
    } catch (err) {
        showToast('Delete failed: ' + err.message, 'error');
    }
}

async function viewPayslip(id) {
    try {
        const p = await apiCall(`/payslip/${id}`);
        document.getElementById('payslipTitle').innerText = `Payslip - ${p.name} (${p.id})`;
        document.getElementById('payslipEmpName').innerText = p.name;
        document.getElementById('payslipEmpId').innerText = p.id;
        document.getElementById('payslipDept').innerText = p.deptName || `Dept ${p.deptId}`;
        document.getElementById('payslipType').innerHTML = getBadge(p.type);

        const breakdownRows = Object.entries(p.breakdown || {})
            .map(([k, v]) => `
                <tr>
                    <td style="color:var(--text-muted);">${escapeHtml(k)}</td>
                    <td style="text-align:right; font-weight:600; color:var(--navy);" class="tabular">${formatCurrency(v)}</td>
                </tr>
            `).join('');

        document.getElementById('payslipBreakdownBody').innerHTML = breakdownRows;
        document.getElementById('payslipNetSalary').innerText = formatCurrency(p.netSalary);
        document.getElementById('payslipBonus10').innerText = formatCurrency(p.bonusPercent);
        document.getElementById('payslipBonusFixed').innerText = formatCurrency(p.bonusFixed);

        openModal('payslipModal');
    } catch (err) {
        showToast('Failed to generate payslip: ' + err.message, 'error');
    }
}

// -------------------------------------------------------------
// 3. PAYROLL PAGE LOGIC
// -------------------------------------------------------------
async function initPayrollPage() {
    await loadDepartmentsForRaise();
    const monthInput = document.getElementById('payrollMonthInput');
    if (monthInput && !monthInput.value) {
        monthInput.value = '2026-11';
    }
    const filterInput = document.getElementById('historyMonthInput');
    if (filterInput && !filterInput.value) {
        filterInput.value = '2026-11';
    }
    await loadHistoricalPayslips();
}

async function loadDepartmentsForRaise() {
    try {
        const depts = await apiCall('/departments');
        const sel = document.getElementById('raiseDeptSelect');
        if (sel) {
            sel.innerHTML = depts.map(d => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join('');
        }
    } catch (err) {
        console.error('Failed to load departments:', err);
    }
}

async function executePayrollRun() {
    const month = document.getElementById('payrollMonthInput')?.value.trim();
    const alertBox = document.getElementById('payrollRunAlert');
    alertBox.style.display = 'none';

    if (!month) {
        showToast('Please select a valid month (YYYY-MM)', 'error');
        return;
    }

    try {
        const res = await apiCall('/payroll/run', 'POST', { month });
        alertBox.className = 'alert alert-success';
        alertBox.innerHTML = `<strong>Success:</strong> ${escapeHtml(res.message)}`;
        alertBox.style.display = 'flex';
        showToast(res.message, 'success');

        // Update history view
        const historyInput = document.getElementById('historyMonthInput');
        if (historyInput) historyInput.value = month;
        await loadHistoricalPayslips();

    } catch (err) {
        alertBox.className = 'alert alert-error';
        alertBox.innerHTML = `<strong>Transaction Rolled Back:</strong> ${escapeHtml(err.message)}`;
        alertBox.style.display = 'flex';
        showToast('Payroll transaction failed and rolled back.', 'error');
    }
}

async function loadHistoricalPayslips() {
    const month = document.getElementById('historyMonthInput')?.value.trim();
    const tbody = document.getElementById('payslipsHistoryTableBody');
    const badge = document.getElementById('historyTotalBadge');
    if (!tbody) return;

    if (!month) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2rem;">Please select a month.</td></tr>`;
        return;
    }

    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2rem; color:var(--text-muted);">Fetching payslips for ${escapeHtml(month)}...</td></tr>`;

    try {
        const list = await apiCall(`/payslips?month=${encodeURIComponent(month)}`);
        if (!list || list.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2.5rem; color:var(--text-muted);">No payslips found for ${escapeHtml(month)}. You can run payroll above.</td></tr>`;
            if (badge) badge.innerText = 'Total Payout: Rs. 0.00 (0 Employees)';
            return;
        }

        let total = 0;
        tbody.innerHTML = list.map(item => {
            total += (item.netSalary || 0);
            return `
                <tr>
                    <td>#${item.slipId}</td>
                    <td><strong>${escapeHtml(item.empId)}</strong></td>
                    <td>${escapeHtml(item.name)}</td>
                    <td>${escapeHtml(item.deptName)}</td>
                    <td>${getBadge(item.empType)}</td>
                    <td class="tabular" style="font-weight:600; color:var(--navy);">${formatCurrency(item.netSalary)}</td>
                </tr>
            `;
        }).join('');

        if (badge) badge.innerText = `Total Payout: ${formatCurrency(total)} (${list.length} Employees)`;

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2rem; color:var(--error);">Failed to load payslips: ${escapeHtml(err.message)}</td></tr>`;
    }
}

async function executeDepartmentRaise() {
    const deptId = parseInt(document.getElementById('raiseDeptSelect')?.value);
    const percent = parseFloat(document.getElementById('raisePercentInput')?.value);
    const alertBox = document.getElementById('raiseAlert');
    alertBox.style.display = 'none';

    if (isNaN(deptId) || isNaN(percent) || percent <= 0) {
        showToast('Please specify a valid department and positive percentage raise.', 'error');
        return;
    }

    if (!confirm(`Are you sure you want to apply a ${percent}% raise to all Full-Time employees in Department ${deptId}?`)) {
        return;
    }

    try {
        const res = await apiCall('/raise', 'POST', { deptId, percent });
        alertBox.className = 'alert alert-success';
        alertBox.innerHTML = `<strong>Raise Applied:</strong> ${escapeHtml(res.message)} Changes are logged in the Audit page.`;
        alertBox.style.display = 'flex';
        showToast(res.message, 'success');
    } catch (err) {
        alertBox.className = 'alert alert-error';
        alertBox.innerHTML = `<strong>Error:</strong> ${escapeHtml(err.message)}`;
        alertBox.style.display = 'flex';
        showToast('Raise failed: ' + err.message, 'error');
    }
}

// -------------------------------------------------------------
// 4. REPORTS PAGE LOGIC
// -------------------------------------------------------------
async function initReportsPage() {
    try {
        const summary = await apiCall('/summary');

        document.getElementById('repTotalPayout').innerText = formatCurrency(summary.totalPayout);
        document.getElementById('repHighestPaid').innerText = summary.highestPaid 
            ? `${summary.highestPaid.name} (${formatCurrency(summary.highestPaid.salary)})`
            : 'None';
        document.getElementById('repHeadcount').innerText = `${summary.employeeCount} Members`;

        // Render Department Payout Chart & Table
        const deptContainer = document.getElementById('deptChartContainer');
        const deptTableBody = document.getElementById('deptTableBody');
        const depts = summary.payoutByDept || [];
        const maxDeptPayout = Math.max(...depts.map(d => d.totalPayout), 1);

        if (deptContainer) {
            deptContainer.innerHTML = depts.map((d, idx) => {
                const pct = Math.round((d.totalPayout / maxDeptPayout) * 100);
                const colorClass = `alt-${(idx % 3) + 1}`;
                return `
                    <div class="chart-bar-item">
                        <div class="chart-bar-header">
                            <span>${escapeHtml(d.department)} (${d.employeeCount} Emps)</span>
                            <span class="tabular">${formatCurrency(d.totalPayout)}</span>
                        </div>
                        <div class="chart-track">
                            <div class="chart-fill ${colorClass}" style="width: ${pct}%"></div>
                        </div>
                    </div>
                `;
            }).join('');
        }

        if (deptTableBody) {
            deptTableBody.innerHTML = depts.map(d => `
                <tr>
                    <td><strong>${escapeHtml(d.department)}</strong></td>
                    <td>${d.employeeCount}</td>
                    <td class="tabular" style="font-weight:600;">${formatCurrency(d.totalPayout)}</td>
                    <td class="tabular" style="color:var(--text-muted);">${formatCurrency(d.averageSalary)}</td>
                </tr>
            `).join('');
        }

        // Render Type Payout Chart
        const typeContainer = document.getElementById('typeChartContainer');
        const payoutByType = summary.payoutByType || {};
        const countByType = summary.countByType || {};
        const totalPayout = summary.totalPayout || 1;

        if (typeContainer) {
            typeContainer.innerHTML = Object.entries(payoutByType).map(([type, amount], idx) => {
                const pct = Math.round((amount / totalPayout) * 100);
                const count = countByType[type] || 0;
                const colorClass = `alt-${(idx % 3) + 1}`;
                return `
                    <div class="chart-bar-item">
                        <div class="chart-bar-header">
                            <span>${getBadge(type)} &nbsp;(${count} Emps)</span>
                            <span class="tabular">${formatCurrency(amount)} (${pct}%)</span>
                        </div>
                        <div class="chart-track">
                            <div class="chart-fill ${colorClass}" style="width: ${pct}%"></div>
                        </div>
                    </div>
                `;
            }).join('');
        }

        // Render Above Average Table
        const aboveTableBody = document.getElementById('aboveAvgTableBody');
        const aboveAvgList = summary.aboveAverage || [];
        if (aboveTableBody) {
            if (aboveAvgList.length === 0) {
                aboveTableBody.innerHTML = `<tr><td colspan="4" style="text-align:center; padding:1.5rem;">No employees above average.</td></tr>`;
            } else {
                aboveTableBody.innerHTML = aboveAvgList.map(a => `
                    <tr>
                        <td><strong>${escapeHtml(a.id)}</strong></td>
                        <td>${escapeHtml(a.name)}</td>
                        <td>${escapeHtml(a.department)}</td>
                        <td class="tabular" style="font-weight:600; color:var(--navy);">${formatCurrency(a.netSalary)}</td>
                    </tr>
                `).join('');
            }
        }

        // Render Top Earners per Dept
        const topDeptBody = document.getElementById('topDeptTableBody');
        const topDeptList = summary.topEarnerByDept || [];
        if (topDeptBody) {
            topDeptBody.innerHTML = topDeptList.map(t => `
                <tr>
                    <td><strong>${escapeHtml(t.department)}</strong></td>
                    <td><strong>${escapeHtml(t.id)}</strong></td>
                    <td>${escapeHtml(t.name)}</td>
                    <td class="tabular" style="font-weight:600; color:var(--navy);">${formatCurrency(t.netSalary)}</td>
                </tr>
            `).join('');
        }

    } catch (err) {
        showToast('Failed to load reports: ' + err.message, 'error');
    }
}

// -------------------------------------------------------------
// 5. AUDIT PAGE LOGIC
// -------------------------------------------------------------
async function initAuditPage() {
    const tbody = document.getElementById('auditTableBody');
    const badge = document.getElementById('auditCountBadge');
    if (!tbody) return;

    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2rem; color:var(--text-muted);">Fetching salary audit logs from MySQL trigger...</td></tr>`;

    try {
        const list = await apiCall('/audit');
        if (badge) badge.innerText = `${list.length} Events Recorded`;

        if (!list || list.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2.5rem; color:var(--text-muted);">No audit entries yet. Salary changes to Full-Time employees automatically trigger audit records.</td></tr>`;
            return;
        }

        tbody.innerHTML = list.map(item => {
            const diff = (item.newBasic || 0) - (item.oldBasic || 0);
            const diffPct = item.oldBasic > 0 ? ((diff / item.oldBasic) * 100).toFixed(1) : '0';
            const diffBadge = diff > 0 
                ? `<span class="badge badge-parttime">+${formatCurrency(diff)} (+${diffPct}%)</span>`
                : `<span class="badge badge-contract">${formatCurrency(diff)} (${diffPct}%)</span>`;

            return `
                <tr>
                    <td>#${item.auditId}</td>
                    <td><strong>${escapeHtml(item.empId)}</strong></td>
                    <td>${escapeHtml(item.name)}</td>
                    <td class="tabular" style="color:var(--text-muted);">${formatCurrency(item.oldBasic)}</td>
                    <td class="tabular" style="font-weight:600; color:var(--navy);">${formatCurrency(item.newBasic)}</td>
                    <td>${diffBadge}</td>
                    <td style="font-size:0.85rem; color:var(--text-muted);">${formatDate(item.changedOn)}</td>
                </tr>
            `;
        }).join('');

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:2rem; color:var(--error);">Failed to load audit history: ${escapeHtml(err.message)}</td></tr>`;
    }
}
