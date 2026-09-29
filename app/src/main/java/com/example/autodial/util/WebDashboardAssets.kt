package com.example.autodial.util

object WebDashboardAssets {

    fun getIndexHtml(): String {
        val S = "${'$'}"
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AUTODIAL - Web Portal</title>
    <style>
        :root {
            --bg-color: #0d1117;
            --card-bg: #161b22;
            --border-color: #30363d;
            --text-main: #f0f6fc;
            --text-sub: #8b949e;
            --accent-green: #00E676;
            --accent-red: #ff5252;
            --accent-blue: #58a6ff;
            --accent-orange: #ff9800;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }

        body {
            background-color: var(--bg-color);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }

        header {
            background-color: var(--card-bg);
            border-bottom: 1px solid var(--border-color);
            padding: 1rem 1.5rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            position: sticky;
            top: 0;
            z-index: 100;
        }

        .logo {
            font-size: 1.25rem;
            font-weight: 800;
            color: var(--accent-green);
            letter-spacing: 1px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .online-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(0, 230, 118, 0.15);
            color: var(--accent-green);
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 600;
        }

        .online-dot {
            width: 8px;
            height: 8px;
            background-color: var(--accent-green);
            border-radius: 50%;
            box-shadow: 0 0 8px var(--accent-green);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 1; }
            50% { opacity: 0.4; }
            100% { opacity: 1; }
        }

        .app-container {
            display: flex;
            flex: 1;
        }

        sidebar {
            width: 240px;
            background: var(--card-bg);
            border-right: 1px solid var(--border-color);
            padding: 20px 0;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .nav-item {
            padding: 12px 24px;
            color: var(--text-sub);
            font-size: 0.9rem;
            font-weight: 600;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 12px;
            transition: all 0.2s;
            border-left: 3px solid transparent;
        }

        .nav-item:hover {
            color: var(--text-main);
            background: rgba(255,255,255,0.03);
        }

        .nav-item.active {
            color: var(--accent-green);
            background: rgba(0, 230, 118, 0.08);
            border-left-color: var(--accent-green);
        }

        main {
            flex: 1;
            padding: 24px;
            max-width: 1200px;
            margin: 0 auto;
            width: 100%;
        }

        .login-container {
            max-width: 400px;
            margin: 80px auto;
            width: 90%;
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 32px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
        }

        .login-title {
            font-size: 1.5rem;
            font-weight: 700;
            margin-bottom: 8px;
            text-align: center;
        }

        .login-subtitle {
            color: var(--text-sub);
            font-size: 0.9rem;
            margin-bottom: 24px;
            text-align: center;
        }

        .form-group {
            margin-bottom: 16px;
        }

        label {
            display: block;
            margin-bottom: 6px;
            font-size: 0.85rem;
            color: var(--text-sub);
            font-weight: 600;
        }

        input[type="text"], input[type="email"], input[type="password"], textarea, select {
            width: 100%;
            padding: 12px;
            background: var(--bg-color);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            color: var(--text-main);
            font-size: 0.95rem;
            outline: none;
            transition: border-color 0.2s;
        }

        input:focus, textarea:focus, select:focus {
            border-color: var(--accent-green);
        }

        .btn {
            padding: 10px 16px;
            background: var(--accent-green);
            color: #000;
            border: none;
            border-radius: 8px;
            font-weight: 700;
            font-size: 0.88rem;
            cursor: pointer;
            transition: transform 0.1s, opacity 0.2s;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
        }

        .btn:hover { opacity: 0.9; }
        .btn:active { transform: scale(0.98); }

        .btn-secondary {
            background: #21262d;
            color: var(--text-main);
            border: 1px solid var(--border-color);
        }

        .btn-secondary:hover { background: #30363d; }

        .btn-danger {
            background: rgba(255, 82, 82, 0.15);
            color: var(--accent-red);
            border: 1px solid var(--accent-red);
        }

        .btn-danger:hover { background: rgba(255, 82, 82, 0.25); }

        .btn-sm {
            padding: 4px 10px;
            font-size: 0.78rem;
            border-radius: 6px;
        }

        .grid-3 {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 20px;
            margin-bottom: 24px;
        }

        .card {
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 24px;
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .card-title {
            font-size: 1rem;
            font-weight: 700;
            color: var(--text-main);
        }

        .stat-val {
            font-size: 1.8rem;
            font-weight: 800;
            color: var(--accent-green);
            margin-top: 4px;
        }

        .stat-sub {
            font-size: 0.8rem;
            color: var(--text-sub);
            margin-top: 4px;
        }

        .filter-pills {
            display: flex;
            gap: 8px;
            margin-bottom: 16px;
            overflow-x: auto;
            padding-bottom: 4px;
        }

        .pill {
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 0.8rem;
            font-weight: 600;
            background: #21262d;
            color: var(--text-sub);
            cursor: pointer;
            border: 1px solid var(--border-color);
            white-space: nowrap;
        }

        .pill.active {
            background: var(--accent-green);
            color: #000;
            border-color: var(--accent-green);
        }

        .table-responsive {
            width: 100%;
            overflow-x: auto;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 0.85rem;
        }

        th, td {
            padding: 12px;
            border-bottom: 1px solid var(--border-color);
        }

        th {
            color: var(--text-sub);
            font-weight: 600;
        }

        .status-pill {
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.72rem;
            font-weight: 700;
            display: inline-block;
        }

        .status-SUCCESS { background: rgba(0,230,118,0.2); color: var(--accent-green); }
        .status-WAITING, .status-QUEUED { background: rgba(88,166,255,0.2); color: var(--accent-blue); }
        .status-FAILED { background: rgba(255,82,82,0.2); color: var(--accent-red); }
        .status-PROCESSING { background: rgba(255,152,0,0.2); color: var(--accent-orange); }
        .status-CANCELLED { background: rgba(139,148,158,0.2); color: var(--text-sub); }

        .notes-list {
            display: flex;
            flex-direction: column;
            gap: 12px;
            max-height: 400px;
            overflow-y: auto;
        }

        .note-item {
            background: var(--bg-color);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 12px;
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
        }

        .chart-box {
            width: 100%;
            height: 220px;
            display: flex;
            align-items: flex-end;
            gap: 12px;
            padding-top: 20px;
            border-bottom: 1px solid var(--border-color);
        }

        .chart-bar-wrap {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            height: 100%;
            justify-content: flex-end;
        }

        .chart-bar {
            width: 80%;
            max-width: 36px;
            background: var(--accent-green);
            border-radius: 4px 4px 0 0;
            transition: height 0.3s;
            min-height: 4px;
        }

        .chart-label {
            font-size: 0.72rem;
            color: var(--text-sub);
            margin-top: 8px;
        }

        .switch-label {
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
        }

        .switch-inp {
            display: none;
        }

        .slider {
            width: 36px;
            height: 20px;
            background: #30363d;
            border-radius: 20px;
            position: relative;
            transition: 0.2s;
        }

        .slider::before {
            content: "";
            position: absolute;
            width: 14px;
            height: 14px;
            background: #fff;
            border-radius: 50%;
            top: 3px;
            left: 3px;
            transition: 0.2s;
        }

        .switch-inp:checked + .slider {
            background: var(--accent-green);
        }

        .switch-inp:checked + .slider::before {
            transform: translateX(16px);
            background: #000;
        }

        .error-msg {
            color: var(--accent-red);
            font-size: 0.85rem;
            margin-top: 8px;
            text-align: center;
        }

        @media (max-width: 768px) {
            .app-container { flex-direction: column; }
            sidebar { width: 100%; flex-direction: row; overflow-x: auto; padding: 8px; border-right: none; border-bottom: 1px solid var(--border-color); }
            .nav-item { padding: 8px 12px; border-left: none; border-bottom: 3px solid transparent; }
            .nav-item.active { border-bottom-color: var(--accent-green); }
        }
    </style>
</head>
<body>

    <header>
        <div class="logo">
            <span>⚡ AUTODIAL WEB PORTAL</span>
        </div>
        <div id="headerStatus" style="display: none; align-items: center; gap: 12px;">
            <span class="online-badge">
                <span class="online-dot"></span>
                <span>LIVE ONLINE</span>
            </span>
            <button class="btn btn-secondary btn-sm" onclick="logout()">Logout</button>
        </div>
    </header>

    <!-- Login View -->
    <div id="loginView" class="login-container">
        <div class="login-title">Sign In</div>
        <div class="login-subtitle">Enter your AUTODIAL terminal credentials</div>
        
        <form onsubmit="handleLogin(event)">
            <div class="form-group">
                <label>Email Address</label>
                <input type="email" id="email" required placeholder="agent@autodial.com">
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" id="password" required placeholder="••••••••">
            </div>
            <button type="submit" class="btn" style="width: 100%;">CONNECT TO TERMINAL</button>
            <div id="loginError" class="error-msg" style="display: none;"></div>
        </form>
    </div>

    <!-- Main App Portal -->
    <div id="appPortal" class="app-container" style="display: none;">
        
        <!-- Sidebar Navigation -->
        <sidebar>
            <div class="nav-item active" onclick="switchNav('dashboard')" id="nav-dashboard">🏠 Dashboard</div>
            <div class="nav-item" onclick="switchNav('transactions')" id="nav-transactions">📜 Transactions</div>
            <div class="nav-item" onclick="switchNav('offers')" id="nav-offers">🏷️ Offers</div>
            <div class="nav-item" onclick="switchNav('clients')" id="nav-clients">👥 Clients</div>
            <div class="nav-item" onclick="switchNav('analytics')" id="nav-analytics">📊 Analytics</div>
            <div class="nav-item" onclick="switchNav('notes')" id="nav-notes">📝 Drop Notes</div>
        </sidebar>

        <main>
            <!-- Dashboard Tab -->
            <div id="view-dashboard">
                <div class="grid-3">
                    <div class="card">
                        <div class="card-header">
                            <span class="card-title">Terminal State</span>
                            <span id="automationStatusBadge" class="status-pill status-SUCCESS">ACTIVE</span>
                        </div>
                        <div class="stat-val" id="deviceAgentId">AGENT --</div>
                        <div class="stat-sub" id="uniqueDeviceId">Device ID: --</div>
                    </div>

                    <div class="card">
                        <div class="card-header">
                            <span class="card-title">Airtime Balances</span>
                        </div>
                        <div style="display: flex; gap: 16px; margin-top: 8px;">
                            <div>
                                <div class="stat-sub">SIM 1</div>
                                <div class="stat-val" style="font-size: 1.4rem;" id="sim1Airtime">KES 0.00</div>
                            </div>
                            <div style="border-left: 1px solid var(--border-color); padding-left: 16px;">
                                <div class="stat-sub">SIM 2</div>
                                <div class="stat-val" style="font-size: 1.4rem;" id="sim2Airtime">KES 0.00</div>
                            </div>
                        </div>
                    </div>

                    <div class="card">
                        <div class="card-header">
                            <span class="card-title">Queue Execution</span>
                        </div>
                        <div class="stat-val" id="queuedCount">0 Pending</div>
                        <div class="stat-sub" id="todayCount">0 Executed Today</div>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Remote Controls</span>
                    </div>
                    <div style="display: flex; flex-wrap: wrap; gap: 12px;">
                        <button class="btn btn-secondary" id="btnToggleAutomation" onclick="toggleAutomation()">Pause Automation</button>
                        <button class="btn btn-secondary" onclick="forceSyncData()">Force Sync Data</button>
                        <button class="btn btn-secondary" onclick="clearFailedQueue()">Clear Queue Lock</button>
                        <button class="btn btn-danger" onclick="triggerEmergencyStop()">Emergency Stop</button>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Recent Activity</span>
                        <button class="btn btn-secondary btn-sm" onclick="fetchDashboard()">Reload</button>
                    </div>
                    <div class="table-responsive">
                        <table>
                            <thead>
                                <tr>
                                    <th>Time</th>
                                    <th>Customer</th>
                                    <th>Offer / Code</th>
                                    <th>Amount</th>
                                    <th>Status</th>
                                </tr>
                            </thead>
                            <tbody id="dashboardTxnTable">
                                <tr><td colspan="5" style="text-align: center; color: var(--text-sub);">Loading activity...</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- Transactions Tab -->
            <div id="view-transactions" style="display: none;">
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Transactions Directory</span>
                        <input type="text" id="txnSearch" placeholder="Search phone or code..." oninput="fetchTransactions()" style="max-width: 240px; padding: 6px 12px; font-size: 0.85rem;">
                    </div>

                    <div class="filter-pills">
                        <div class="pill active" onclick="setTxnFilter('ALL', this)">All</div>
                        <div class="pill" onclick="setTxnFilter('SUCCESS', this)">Success</div>
                        <div class="pill" onclick="setTxnFilter('WAITING', this)">Pending / Queued</div>
                        <div class="pill" onclick="setTxnFilter('FAILED', this)">Failed</div>
                        <div class="pill" onclick="setTxnFilter('CANCELLED', this)">Cancelled</div>
                    </div>

                    <div class="table-responsive">
                        <table>
                            <thead>
                                <tr>
                                    <th>Date & Time</th>
                                    <th>Phone Number</th>
                                    <th>Offer / Code</th>
                                    <th>Amount</th>
                                    <th>Status</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody id="fullTxnTable">
                                <tr><td colspan="6" style="text-align: center; color: var(--text-sub);">Loading transactions...</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- Offers Tab -->
            <div id="view-offers" style="display: none;">
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Configured Offers Catalog</span>
                        <button class="btn btn-secondary btn-sm" onclick="fetchOffers()">Refresh Offers</button>
                    </div>

                    <div class="table-responsive">
                        <table>
                            <thead>
                                <tr>
                                    <th>Offer Name</th>
                                    <th>Price / Amount</th>
                                    <th>SAF Cost</th>
                                    <th>Mode</th>
                                    <th>Status</th>
                                    <th>Enable / Disable</th>
                                </tr>
                            </thead>
                            <tbody id="offersTable">
                                <tr><td colspan="6" style="text-align: center; color: var(--text-sub);">Loading offers...</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- Clients Tab -->
            <div id="view-clients" style="display: none;">
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Clients Directory</span>
                        <input type="text" id="clientSearch" placeholder="Search name or phone..." oninput="fetchClients()" style="max-width: 240px; padding: 6px 12px; font-size: 0.85rem;">
                    </div>

                    <div class="table-responsive">
                        <table>
                            <thead>
                                <tr>
                                    <th>Client Name</th>
                                    <th>Phone Number</th>
                                    <th>Purchases</th>
                                    <th>Total Spent</th>
                                    <th>Outstanding Debt</th>
                                </tr>
                            </thead>
                            <tbody id="clientsTable">
                                <tr><td colspan="5" style="text-align: center; color: var(--text-sub);">Loading clients...</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- Analytics Tab -->
            <div id="view-analytics" style="display: none;">
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">Commission & Sales Analytics (7 Days)</span>
                    </div>
                    <div class="chart-box" id="commissionChart">
                        <!-- Rendered dynamically -->
                    </div>
                </div>

                <div class="grid-3">
                    <div class="card">
                        <div class="card-title">Total Commission Earned</div>
                        <div class="stat-val" id="analyticsTotalCommission">KES 0.00</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Today's Airtime Sales</div>
                        <div class="stat-val" id="analyticsTodayAirtime">KES 0.00</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Active Success Rate</div>
                        <div class="stat-val" id="analyticsSuccessRate">100%</div>
                    </div>
                </div>
            </div>

            <!-- Notes Tab -->
            <div id="view-notes" style="display: none;">
                <div class="grid-3">
                    <div class="card">
                        <div class="card-header">
                            <span class="card-title">Drop New Note</span>
                        </div>
                        <form onsubmit="handleDropNote(event)">
                            <div class="form-group">
                                <textarea id="noteText" rows="4" placeholder="Write a note or instructions for terminal operator..." required></textarea>
                            </div>
                            <button type="submit" class="btn" style="width: 100%;">POST NOTE TO APP</button>
                        </form>
                    </div>

                    <div class="card" style="grid-column: span 2;">
                        <div class="card-header">
                            <span class="card-title">Terminal Shared Notes</span>
                            <button class="btn btn-secondary btn-sm" onclick="fetchNotes()">Refresh</button>
                        </div>
                        <div id="notesList" class="notes-list">
                            <div class="stat-sub" style="text-align: center; padding: 12px;">No notes posted yet.</div>
                        </div>
                    </div>
                </div>
            </div>
        </main>
    </div>

    <script>
        let authToken = localStorage.getItem('autodial_web_token') || '';
        let activeNav = 'dashboard';
        let txnFilter = 'ALL';
        let automationEnabled = true;

        if (authToken) {
            showPortal();
        }

        async function apiCall(endpoint, method = 'GET', body = null) {
            const headers = { 'Content-Type': 'application/json' };
            if (authToken) headers['Authorization'] = 'Bearer ' + authToken;

            const opts = { method, headers };
            if (body) opts.body = JSON.stringify(body);

            try {
                const res = await fetch(endpoint, opts);
                if (res.status === 401) {
                    logout();
                    throw new Error('Unauthorized');
                }
                return await res.json();
            } catch (err) {
                console.error(err);
                throw err;
            }
        }

        async function handleLogin(e) {
            e.preventDefault();
            const email = document.getElementById('email').value.trim();
            const password = document.getElementById('password').value.trim();
            const errDiv = document.getElementById('loginError');
            errDiv.style.display = 'none';

            try {
                const res = await apiCall('/api/login', 'POST', { email, password });
                if (res.success && res.token) {
                    authToken = res.token;
                    localStorage.setItem('autodial_web_token', authToken);
                    showPortal();
                } else {
                    errDiv.innerText = res.message || 'Login failed. Invalid credentials.';
                    errDiv.style.display = 'block';
                }
            } catch (err) {
                errDiv.innerText = 'Network error or app server unreachable.';
                errDiv.style.display = 'block';
            }
        }

        function showPortal() {
            document.getElementById('loginView').style.display = 'none';
            document.getElementById('appPortal').style.display = 'flex';
            document.getElementById('headerStatus').style.display = 'flex';
            switchNav('dashboard');
            setInterval(autoRefresh, 8000);
        }

        function logout() {
            authToken = '';
            localStorage.removeItem('autodial_web_token');
            document.getElementById('loginView').style.display = 'block';
            document.getElementById('appPortal').style.display = 'none';
            document.getElementById('headerStatus').style.display = 'none';
        }

        function switchNav(nav) {
            activeNav = nav;
            ['dashboard', 'transactions', 'offers', 'clients', 'analytics', 'notes'].forEach(n => {
                document.getElementById('view-' + n).style.display = (n === nav) ? 'block' : 'none';
                const navEl = document.getElementById('nav-' + n);
                if (navEl) {
                    if (n === nav) navEl.classList.add('active');
                    else navEl.classList.remove('active');
                }
            });

            if (nav === 'dashboard') fetchDashboard();
            else if (nav === 'transactions') fetchTransactions();
            else if (nav === 'offers') fetchOffers();
            else if (nav === 'clients') fetchClients();
            else if (nav === 'analytics') fetchAnalytics();
            else if (nav === 'notes') fetchNotes();
        }

        function autoRefresh() {
            if (!authToken) return;
            if (activeNav === 'dashboard') fetchDashboard();
            else if (activeNav === 'transactions') fetchTransactions();
        }

        async function fetchDashboard() {
            try {
                const data = await apiCall('/api/dashboard');
                if (!data) return;

                document.getElementById('deviceAgentId').innerText = 'AGENT ' + (data.agentId || '001');
                document.getElementById('uniqueDeviceId').innerText = 'Device ID: ' + (data.deviceId || 'AUTODIAL');
                document.getElementById('sim1Airtime').innerText = 'KES ' + (data.sim1Airtime || '0.00');
                document.getElementById('sim2Airtime').innerText = 'KES ' + (data.sim2Airtime || '0.00');
                document.getElementById('queuedCount').innerText = (data.queuedCount || 0) + ' Pending';
                document.getElementById('todayCount').innerText = (data.todayCount || 0) + ' Executed Today';

                automationEnabled = data.automationEnabled;
                const btn = document.getElementById('btnToggleAutomation');
                const badge = document.getElementById('automationStatusBadge');

                if (automationEnabled) {
                    btn.innerText = 'Pause Automation';
                    btn.className = 'btn btn-secondary';
                    badge.innerText = 'ACTIVE';
                    badge.className = 'status-pill status-SUCCESS';
                } else {
                    btn.innerText = 'Resume Automation';
                    btn.className = 'btn';
                    badge.innerText = 'PAUSED';
                    badge.className = 'status-pill status-FAILED';
                }

                renderTransactionsTable('dashboardTxnTable', data.recentTransactions || [], false);
            } catch (e) {
                console.error(e);
            }
        }

        async function fetchTransactions() {
            const query = document.getElementById('txnSearch')?.value || '';
            try {
                const data = await apiCall(`/api/transactions?filter=${S}{txnFilter}&q=${S}{encodeURIComponent(query)}`);
                renderTransactionsTable('fullTxnTable', data.transactions || [], true);
            } catch (e) {
                console.error(e);
            }
        }

        function setTxnFilter(filter, el) {
            txnFilter = filter;
            document.querySelectorAll('.filter-pills .pill').forEach(p => p.classList.remove('active'));
            if (el) el.classList.add('active');
            fetchTransactions();
        }

        function renderTransactionsTable(tbodyId, txns, showActions) {
            const tbody = document.getElementById(tbodyId);
            if (!txns || txns.length === 0) {
                tbody.innerHTML = `<tr><td colspan="${S}{showActions ? 6 : 5}" style="text-align: center; color: var(--text-sub);">No transactions found.</td></tr>`;
                return;
            }

            tbody.innerHTML = txns.map(t => `
                <tr>
                    <td>${S}{new Date(t.date).toLocaleString()}</td>
                    <td><b>${S}{t.customerNumber || '--'}</b></td>
                    <td>${S}{t.offerName || t.transactionCode || 'Airtime'}</td>
                    <td>KES ${S}{(t.amount || 0).toFixed(2)}</td>
                    <td><span class="status-pill status-${S}{t.status || 'SUCCESS'}">${S}{t.status}</span></td>
                    ${S}{showActions ? `
                        <td>
                            <button class="btn btn-secondary btn-sm" onclick="retryTxn(${S}{t.id})">Retry</button>
                            <button class="btn btn-secondary btn-sm" onclick="cancelTxn(${S}{t.id})">Cancel</button>
                            <button class="btn btn-danger btn-sm" onclick="deleteTxn(${S}{t.id})">Delete</button>
                        </td>
                    ` : ''}
                </tr>
            `).join('');
        }

        async function retryTxn(id) {
            try {
                await apiCall('/api/transactions/retry', 'POST', { id });
                fetchTransactions();
            } catch (e) { alert('Failed to retry transaction'); }
        }

        async function cancelTxn(id) {
            try {
                await apiCall('/api/transactions/cancel', 'POST', { id });
                fetchTransactions();
            } catch (e) { alert('Failed to cancel transaction'); }
        }

        async function deleteTxn(id) {
            if (confirm('Delete this transaction record?')) {
                try {
                    await apiCall('/api/transactions/delete', 'POST', { id });
                    fetchTransactions();
                } catch (e) { alert('Failed to delete transaction'); }
            }
        }

        async function fetchOffers() {
            try {
                const data = await apiCall('/api/offers');
                const tbody = document.getElementById('offersTable');
                if (!data.offers || data.offers.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-sub);">No offers configured.</td></tr>';
                    return;
                }

                tbody.innerHTML = data.offers.map(o => `
                    <tr>
                        <td><b>${S}{o.name}</b></td>
                        <td>KES ${S}{(o.amount || 0).toFixed(2)}</td>
                        <td>KES ${S}{(o.safPrice || 0).toFixed(2)}</td>
                        <td>${S}{o.mode || 'SIMPLE'}</td>
                        <td><span class="status-pill status-${S}{o.isEnabled ? 'SUCCESS' : 'CANCELLED'}">${S}{o.isEnabled ? 'ENABLED' : 'DISABLED'}</span></td>
                        <td>
                            <label class="switch-label">
                                <input type="checkbox" class="switch-inp" ${S}{o.isEnabled ? 'checked' : ''} onchange="toggleOffer(${S}{o.id}, this.checked)">
                                <span class="slider"></span>
                            </label>
                        </td>
                    </tr>
                `).join('');
            } catch (e) { console.error(e); }
        }

        async function toggleOffer(id, enabled) {
            try {
                await apiCall('/api/offers/toggle', 'POST', { id, enabled });
                fetchOffers();
            } catch (e) { alert('Failed to update offer'); }
        }

        async function fetchClients() {
            const query = document.getElementById('clientSearch')?.value || '';
            try {
                const data = await apiCall(`/api/clients?q=${S}{encodeURIComponent(query)}`);
                const tbody = document.getElementById('clientsTable');
                if (!data.clients || data.clients.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-sub);">No clients found.</td></tr>';
                    return;
                }

                tbody.innerHTML = data.clients.map(c => `
                    <tr>
                        <td><b>${S}{c.name || 'Unnamed Client'}</b></td>
                        <td>${S}{c.phoneNumber}</td>
                        <td>${S}{c.totalPurchases || 0}</td>
                        <td>KES ${S}{(c.totalSpent || 0).toFixed(2)}</td>
                        <td>
                            ${S}{c.outstandingDebt > 0 ? 
                                `<span class="status-pill status-FAILED">KES ${S}{c.outstandingDebt.toFixed(2)}</span>` : 
                                `<span class="status-pill status-SUCCESS">NO DEBT</span>`}
                        </td>
                    </tr>
                `).join('');
            } catch (e) { console.error(e); }
        }

        async function fetchAnalytics() {
            try {
                const data = await apiCall('/api/analytics');
                document.getElementById('analyticsTotalCommission').innerText = 'KES ' + (data.totalCommission || '0.00');
                document.getElementById('analyticsTodayAirtime').innerText = 'KES ' + (data.todayAirtime || '0.00');
                document.getElementById('analyticsSuccessRate').innerText = (data.successRate || '100') + '%';

                const chart = document.getElementById('commissionChart');
                if (data.dailyTrend && data.dailyTrend.length > 0) {
                    const maxVal = Math.max(...data.dailyTrend.map(d => d.value), 10);
                    chart.innerHTML = data.dailyTrend.map(d => `
                        <div class="chart-bar-wrap">
                            <div class="chart-bar" style="height: ${S}{(d.value / maxVal) * 100}%;" title="KES ${S}{d.value.toFixed(2)}"></div>
                            <div class="chart-label">${S}{d.day}</div>
                        </div>
                    `).join('');
                } else {
                    chart.innerHTML = '<div style="margin: auto; color: var(--text-sub);">No analytics data yet.</div>';
                }
            } catch (e) { console.error(e); }
        }

        async function handleDropNote(e) {
            e.preventDefault();
            const text = document.getElementById('noteText').value.trim();
            if (!text) return;

            try {
                const res = await apiCall('/api/notes', 'POST', { note: text });
                if (res.success) {
                    document.getElementById('noteText').value = '';
                    fetchNotes();
                }
            } catch (e) { alert('Failed to drop note'); }
        }

        async function fetchNotes() {
            try {
                const data = await apiCall('/api/notes');
                const list = document.getElementById('notesList');
                if (data.notes && data.notes.length > 0) {
                    list.innerHTML = data.notes.map(n => `
                        <div class="note-item">
                            <div>
                                <div style="font-size: 0.9rem;">${S}{escapeHtml(n.text)}</div>
                                <div style="font-size: 0.75rem; color: var(--text-sub); margin-top: 4px;">${S}{new Date(n.timestamp).toLocaleString()} • Posted from Web</div>
                            </div>
                            <button class="btn btn-danger btn-sm" onclick="deleteNote(${S}{n.id})">Delete</button>
                        </div>
                    `).join('');
                } else {
                    list.innerHTML = '<div class="stat-sub" style="text-align: center; padding: 12px;">No notes posted yet.</div>';
                }
            } catch (e) { console.error(e); }
        }

        async function deleteNote(id) {
            try {
                await apiCall('/api/notes/delete', 'POST', { id });
                fetchNotes();
            } catch (e) { alert('Failed to delete note'); }
        }

        async function toggleAutomation() {
            try {
                await apiCall('/api/control/automation', 'POST', { enabled: !automationEnabled });
                fetchDashboard();
            } catch (e) { alert('Failed to toggle automation'); }
        }

        async function forceSyncData() {
            try {
                await apiCall('/api/control/sync', 'POST');
                alert('Sync signal sent to mobile app terminal!');
            } catch (e) { alert('Failed to sync'); }
        }

        async function clearFailedQueue() {
            try {
                await apiCall('/api/control/clear-queue-flag', 'POST');
                alert('Queue failure flag cleared!');
            } catch (e) { alert('Failed'); }
        }

        async function triggerEmergencyStop() {
            if (confirm('Emergency Stop will pause queue processing! Proceed?')) {
                try {
                    await apiCall('/api/control/emergency-stop', 'POST');
                    fetchDashboard();
                } catch (e) { alert('Error executing emergency stop'); }
            }
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
