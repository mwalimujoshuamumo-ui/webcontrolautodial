// Firebase Config
const firebaseConfig = {
    apiKey: "AIzaSyAQcAQaIPHosrAQRgxjv4rUuFAeLw-3jcE",
    authDomain: "adminautodial.firebaseapp.com",
    projectId: "adminautodial",
    storageBucket: "adminautodial.firebasestorage.app",
    messagingSenderId: "719687634682",
    appId: "1:719687634682:web:autodialweb"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.firestore();

// UI Elements
const authView = document.getElementById('authView');
const dashboardView = document.getElementById('dashboardView');
const loginForm = document.getElementById('loginForm');
const emailInput = document.getElementById('email');
const passwordInput = document.getElementById('password');
const authError = document.getElementById('authError');
const logoutBtn = document.getElementById('logoutBtn');
const userEmailSpan = document.getElementById('userEmail');

const statusBadge = document.getElementById('statusBadge');
const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');
const automationToggle = document.getElementById('automationToggle');
const automationSubtext = document.getElementById('automationSubtext');

const metricDevice = document.getElementById('metricDevice');
const metricSim1 = document.getElementById('metricSim1');
const metricSim2 = document.getElementById('metricSim2');
const metricCommission = document.getElementById('metricCommission');
const metricRate = document.getElementById('metricRate');

const txnsTableBody = document.getElementById('txnsTableBody');
const txnCounter = document.getElementById('txnCounter');
const noteInput = document.getElementById('noteInput');
const sendNoteBtn = document.getElementById('sendNoteBtn');

let currentDeviceId = null;
let activeUnsubscribers = [];

// Auth State Observer
auth.onAuthStateChanged((user) => {
    if (user) {
        authView.classList.add('hidden');
        dashboardView.classList.remove('hidden');
        userEmailSpan.textContent = user.email || user.uid;
        initDashboard(user);
    } else {
        clearSubscriptions();
        authView.classList.remove('hidden');
        dashboardView.classList.add('hidden');
    }
});

// Login Form Submit
loginForm.addEventListener('submit', (e) => {
    e.preventDefault();
    authError.classList.add('hidden');
    const email = emailInput.value.trim();
    const password = passwordInput.value;

    auth.signInWithEmailAndPassword(email, password)
        .catch((err) => {
            authError.textContent = err.message || "Failed to sign in";
            authError.classList.remove('hidden');
        });
});

// Logout
logoutBtn.addEventListener('click', () => {
    auth.signOut();
});

function clearSubscriptions() {
    activeUnsubscribers.forEach(unsub => unsub());
    activeUnsubscribers = [];
}

// Initialize Real-time Listeners
function initDashboard(user) {
    clearSubscriptions();

    // Listen to Devices Collection
    const deviceUnsub = db.collection('devices')
        .where('userEmail', '==', user.email)
        .onSnapshot((snapshot) => {
            if (snapshot.empty) {
                // Try looking up by authUid
                db.collection('devices')
                    .where('authUid', '==', user.uid)
                    .get()
                    .then((altSnapshot) => {
                        if (!altSnapshot.empty) {
                            renderDeviceData(altSnapshot.docs[0].data(), altSnapshot.docs[0].id);
                        } else {
                            renderNoDevice();
                        }
                    });
            } else {
                const doc = snapshot.docs[0];
                renderDeviceData(doc.data(), doc.id);
            }
        }, (err) => {
            console.error("Device listener error:", err);
        });

    activeUnsubscribers.push(deviceUnsub);
}

function renderDeviceData(data, deviceId) {
    currentDeviceId = deviceId;
    metricDevice.textContent = data.deviceName || deviceId;

    const online = data.online === true;
    if (online) {
        statusBadge.className = 'badge-online';
        statusDot.className = 'dot-online';
        statusText.textContent = 'TERMINAL ONLINE';
    } else {
        statusBadge.className = 'badge-offline';
        statusDot.className = 'dot-offline';
        statusText.textContent = 'TERMINAL OFFLINE';
    }

    const isAuto = data.automationEnabled !== false;
    automationToggle.checked = isAuto;
    automationSubtext.textContent = isAuto
        ? "Automation engine is ACTIVE on terminal"
        : "Automation engine is PAUSED on terminal";

    metricSim1.textContent = (data.airtimeSim1 || 0).toFixed(2) + " KES";
    metricSim2.textContent = (data.airtimeSim2 || 0).toFixed(2) + " KES";
    metricCommission.textContent = (data.totalCommission || 0).toFixed(2) + " KES";
    metricRate.textContent = (data.successRate || 0) + "%";

    // Setup Transactions listener for this deviceId
    listenTransactions(deviceId);
}

function renderNoDevice() {
    statusBadge.className = 'badge-offline';
    statusDot.className = 'dot-offline';
    statusText.textContent = 'NO TERMINAL LINKED';
    metricDevice.textContent = 'None';
}

// Listen to Transactions Stream
function listenTransactions(deviceId) {
    const txnUnsub = db.collection('transactions')
        .where('deviceId', '==', deviceId)
        .limit(100)
        .onSnapshot((snapshot) => {
            if (snapshot.empty) {
                txnsTableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 24px;">No transactions recorded for this terminal yet.</td></tr>`;
                txnCounter.textContent = "0 transactions";
                return;
            }

            txnCounter.textContent = `${snapshot.docs.length} recent transactions`;
            let html = '';

            const docs = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
            docs.sort((a, b) => (b.date || 0) - (a.date || 0));

            docs.forEach(t => {
                const dateStr = t.date ? new Date(t.date).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'medium' }) : '--';
                const status = (t.status || 'WAITING').toUpperCase();

                let badgeClass = 'badge-waiting';
                if (status === 'SUCCESS' || status === 'COMPLETED') badgeClass = 'badge-success';
                else if (status === 'FAILED' || status === 'CANCELLED') badgeClass = 'badge-failed';
                else if (status === 'PROCESSING') badgeClass = 'badge-processing';

                html += `
                    <tr>
                        <td>${dateStr}</td>
                        <td><strong>${t.customerNumber || '--'}</strong></td>
                        <td>${t.offerName || 'Airtime'} <br><small style="color: var(--text-muted);">${t.transactionCode || ''}</small></td>
                        <td><strong>KES ${(t.amount || 0).toFixed(2)}</strong></td>
                        <td><span class="badge-status ${badgeClass}">${status}</span></td>
                        <td>
                            ${status === 'WAITING' || status === 'FAILED' ? `<button onclick="retryTxn('${t.id}')" class="btn-sm-outline">Retry</button>` : ''}
                        </td>
                    </tr>
                `;
            });

            txnsTableBody.innerHTML = html;
        }, (err) => {
            console.error("Transactions listener error:", err);
        });

    activeUnsubscribers.push(txnUnsub);
}

// Toggle Remote Automation
automationToggle.addEventListener('change', () => {
    if (!currentDeviceId) return;
    const enabled = automationToggle.checked;
    db.collection('devices').document(currentDeviceId).update({
        automationEnabled: enabled
    }).catch(err => {
        console.error("Failed to update automation state:", err);
        automationToggle.checked = !enabled;
    });
});

// Retry Transaction
window.retryTxn = function(docId) {
    db.collection('transactions').doc(docId).update({
        status: 'WAITING'
    }).then(() => {
        alert("Transaction set to WAITING for retry.");
    }).catch(err => {
        alert("Error: " + err.message);
    });
};

// Send Note to Mobile Terminal
sendNoteBtn.addEventListener('click', () => {
    const text = noteInput.value.trim();
    if (!text || !currentDeviceId) return;

    db.collection('agent_logs').add({
        deviceId: currentDeviceId,
        message: "[WEB NOTE]: " + text,
        type: "note",
        timestamp: firebase.firestore.FieldValue.serverTimestamp()
    }).then(() => {
        noteInput.value = '';
        alert("Note sent to terminal logs!");
    }).catch(err => {
        alert("Error sending note: " + err.message);
    });
});
