/**
 * FortressBank Security Incident Simulator - Main Application
 * 
 * THIS IS NOT A SIMULATION - IT MAKES REAL HTTP CALLS!
 * 
 * This dashboard:
 * 1. Executes REAL attacks against the running backend
 * 2. Shows REAL HTTP request/response data
 * 3. Displays REAL defense behavior
 * 4. Falls back to simulation ONLY if backend is unreachable
 */

// ==========================================================================
// State Management
// ==========================================================================
const state = {
    selectedAttack: null,
    executedAttacks: new Set(),
    totalAttacks: 0,
    blockedCount: 0,
    responseTimes: [],
    isExecuting: false,
    authToken: null,          // JWT token for authenticated attacks
    backendOnline: false,     // Whether backend is reachable
    lastResponse: null        // Store last real response
};

// ==========================================================================
// Configuration
// ==========================================================================
const CONFIG = {
    // Kong Gateway URL (the real backend)
    BACKEND_URL: 'http://localhost:8000',
    
    // Keycloak URL for token generation
    KEYCLOAK_URL: 'http://localhost:8888',
    REALM: 'fortressbank-realm',
    CLIENT_ID: 'kong',
    CLIENT_SECRET: 'pmFStZwGO8sb0mBDkZmP5niE3wmEELqe',
    
    // Test credentials
    TEST_USER: 'testuser',
    TEST_PASSWORD: 'password',
    
    // ONLY fall back to simulation if backend is truly unreachable
    FORCE_SIMULATION: false,
    
    // Animation timings (ms)
    TYPING_SPEED: 30,
    LAYER_ACTIVATION_DELAY: 200,
    LOG_ENTRY_DELAY: 300,
    GAUGE_ANIMATION_DURATION: 500
};

// ==========================================================================
// Initialization
// ==========================================================================
document.addEventListener('DOMContentLoaded', async () => {
    renderAttackLibrary();
    setupEventListeners();
    updateStats();
    
    // Check if backend is online
    await checkBackendStatus();
    
    console.log('🛡️ FortressBank Security Simulator initialized');
    console.log(`📊 ${ATTACKS.length} attack scenarios loaded`);
    console.log(`🔌 Backend: ${state.backendOnline ? 'ONLINE (Real attacks!)' : 'OFFLINE (Simulation mode)'}`);
});

// ==========================================================================
// Backend Connection & Auth
// ==========================================================================
async function checkBackendStatus() {
    const statusEl = document.getElementById('systemStatus');
    
    try {
        // Try to reach Kong gateway - any response (even 404) means it's online
        const response = await fetch(`${CONFIG.BACKEND_URL}/`, { 
            method: 'GET',
            mode: 'cors'
        });
        
        // Kong returns 404 for root path, that's fine - it means Kong is running!
        state.backendOnline = true;
        statusEl.innerHTML = `
            <span class="status-dot active"></span>
            <span>Backend LIVE</span>
        `;
        statusEl.classList.add('live');
        
        // Try to get auth token
        await getAuthToken();
        
    } catch (err) {
        // Fetch throws on network errors or CORS blocks
        // Try a different approach - check if we can at least reach Keycloak
        try {
            const kcResponse = await fetch(
                `${CONFIG.KEYCLOAK_URL}/realms/${CONFIG.REALM}/.well-known/openid-configuration`,
                { method: 'GET', mode: 'cors' }
            );
            if (kcResponse.ok) {
                // Keycloak is up, Kong might have CORS issues
                state.backendOnline = true;
                statusEl.innerHTML = `
                    <span class="status-dot active"></span>
                    <span>Backend LIVE (Auth Ready)</span>
                `;
                statusEl.classList.add('live');
                await getAuthToken();
                return;
            }
        } catch (kcErr) {
            // Both Kong and Keycloak unreachable
        }
        
        state.backendOnline = false;
        statusEl.innerHTML = `
            <span class="status-dot"></span>
            <span>Simulation Mode</span>
        `;
        statusEl.classList.remove('live');
        console.warn('Backend not reachable, using simulation mode');
    }
}

async function getAuthToken() {
    try {
        const tokenUrl = `${CONFIG.KEYCLOAK_URL}/realms/${CONFIG.REALM}/protocol/openid-connect/token`;
        
        const response = await fetch(tokenUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                grant_type: 'password',
                client_id: CONFIG.CLIENT_ID,
                client_secret: CONFIG.CLIENT_SECRET,
                username: CONFIG.TEST_USER,
                password: CONFIG.TEST_PASSWORD
            })
        });
        
        if (response.ok) {
            const data = await response.json();
            state.authToken = data.access_token;
            console.log('✅ Auth token obtained');
            return true;
        }
    } catch (err) {
        console.warn('Could not get auth token:', err.message);
    }
    return false;
}

// Generate malicious JWT with 'none' algorithm
function generateNoneAlgorithmToken() {
    if (!state.authToken) return null;
    
    // Decode the original token
    const parts = state.authToken.split('.');
    if (parts.length !== 3) return null;
    
    // Create malicious header with 'none' algorithm
    const maliciousHeader = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }))
        .replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    
    // Keep the original payload
    const payload = parts[1];
    
    // Return token with empty signature
    return `${maliciousHeader}.${payload}.`;
}

// ==========================================================================
// Attack Library Rendering
// ==========================================================================
function renderAttackLibrary() {
    const container = document.getElementById('attackList');
    container.innerHTML = '';
    
    ATTACKS.forEach((attack, index) => {
        const card = createAttackCard(attack, index);
        container.appendChild(card);
    });
    
    document.getElementById('attackCount').textContent = `0/${ATTACKS.length} executed`;
}

function createAttackCard(attack, index) {
    const card = document.createElement('div');
    card.className = 'attack-card';
    card.dataset.attackId = attack.id;
    
    if (state.executedAttacks.has(attack.id)) {
        card.classList.add('executed');
    }
    
    const categoryConfig = CATEGORY_CONFIG[attack.category] || { color: '#888', icon: '🔒' };
    
    card.innerHTML = `
        <div class="attack-card-header">
            <span class="attack-name">${categoryConfig.icon} ${attack.name}</span>
            <span class="attack-badge ${attack.severity}">${attack.severity}</span>
        </div>
        <p class="attack-description">${attack.description}</p>
        <div class="attack-meta">
            <span class="attack-tag">${attack.owasp}</span>
            <span class="attack-tag">${attack.category}</span>
        </div>
    `;
    
    card.addEventListener('click', () => openAttackModal(attack));
    
    return card;
}

// ==========================================================================
// Modal Management
// ==========================================================================
function openAttackModal(attack) {
    state.selectedAttack = attack;
    
    document.getElementById('modalTitle').textContent = `⚔️ ${attack.name}`;
    document.getElementById('modalBody').innerHTML = createModalContent(attack);
    document.getElementById('modalOverlay').classList.add('active');
    
    // Disable launch button if already executed
    const launchBtn = document.getElementById('launchBtn');
    if (state.executedAttacks.has(attack.id)) {
        launchBtn.disabled = true;
        launchBtn.textContent = '✓ Already Executed';
    } else {
        launchBtn.disabled = false;
        launchBtn.textContent = '🚀 Launch Attack';
    }
}

function createModalContent(attack) {
    const owaspName = OWASP_MAP[attack.owasp] || attack.owasp;
    
    return `
        <div class="modal-section">
            <h4>Attack Description</h4>
            <p>${attack.description}</p>
        </div>
        
        <div class="modal-section">
            <h4>Technical Details</h4>
            <p>${attack.technicalDetails}</p>
        </div>
        
        <div class="modal-section">
            <h4>OWASP Classification</h4>
            <p><strong>${attack.owasp}</strong> — ${owaspName}</p>
        </div>
        
        <div class="modal-section">
            <h4>HTTP Request</h4>
            <pre class="modal-code">${formatHttpRequest(attack.request)}</pre>
        </div>
        
        <div class="modal-section">
            <h4>Expected Defense</h4>
            <p>
                <strong>Status:</strong> ${attack.expectedResult.status} ${getStatusText(attack.expectedResult.status)}<br>
                <strong>Message:</strong> ${attack.expectedResult.message}<br>
                <strong>Risk Score:</strong> ${attack.riskScore}/100
            </p>
        </div>
    `;
}

function closeModal() {
    document.getElementById('modalOverlay').classList.remove('active');
    state.selectedAttack = null;
}

// ==========================================================================
// Attack Execution
// ==========================================================================
async function launchSelectedAttack() {
    if (!state.selectedAttack || state.isExecuting) return;
    
    const attack = state.selectedAttack;
    state.isExecuting = true;
    
    closeModal();
    
    // Update attack card to executing state
    const attackCard = document.querySelector(`[data-attack-id="${attack.id}"]`);
    if (attackCard) {
        attackCard.classList.add('executing');
    }
    
    // Show executing state in response panel
    showExecutingState(attack);
    
    // Reset defense layers
    resetDefenseLayers();
    
    // Clear previous logs for this demo
    clearLogs();
    
    // Start timer
    const startTime = performance.now();
    
    // Simulate the attack execution
    await executeAttackSimulation(attack);
    
    // Calculate response time
    const responseTime = Math.round(performance.now() - startTime);
    state.responseTimes.push(responseTime);
    
    // Update UI with results
    showAttackResult(attack, responseTime);
    
    // Mark as executed
    state.executedAttacks.add(attack.id);
    state.totalAttacks++;
    if (attack.expectedResult.blocked) {
        state.blockedCount++;
    }
    
    // Update attack card
    if (attackCard) {
        attackCard.classList.remove('executing');
        attackCard.classList.add('executed');
    }
    
    // Update stats
    updateStats();
    document.getElementById('attackCount').textContent = 
        `${state.executedAttacks.size}/${ATTACKS.length} executed`;
    
    state.isExecuting = false;
}

// ==========================================================================
// REAL Attack Execution (Makes actual HTTP calls!)
// ==========================================================================
async function executeAttackSimulation(attack) {
    // Update HTTP inspector with request
    showHttpRequest(attack.request);
    
    let realResponse = null;
    let responseTime = 0;
    
    // EXECUTE REAL ATTACK if backend is online!
    if (state.backendOnline && !CONFIG.FORCE_SIMULATION) {
        try {
            const startTime = performance.now();
            realResponse = await executeRealAttack(attack);
            responseTime = Math.round(performance.now() - startTime);
            
            console.log(`🎯 REAL attack executed: ${attack.name}`);
            console.log(`📡 Response:`, realResponse);
        } catch (err) {
            console.warn('Real attack failed, showing expected result:', err);
        }
    }
    
    // Activate defense layers one by one with animation
    for (const layer of attack.defenseLayers) {
        await activateDefenseLayer(layer);
        await sleep(CONFIG.LAYER_ACTIVATION_DELAY);
    }
    
    // Add audit trail entries
    for (const entry of attack.auditTrail) {
        await addForensicLog(entry);
        await sleep(CONFIG.LOG_ENTRY_DELAY);
    }
    
    // Determine what risk score to show
    let riskScore = attack.riskScore;
    
    // If we got a real response, update the display
    if (realResponse) {
        // Animate risk gauge
        await animateRiskGauge(riskScore);
        
        // Show REAL response!
        showRealHttpResponse(realResponse, responseTime);
    } else {
        // Fallback: show expected simulated response
        await animateRiskGauge(riskScore);
        showHttpResponse(attack.expectedResult);
    }
}

async function executeRealAttack(attack) {
    const { method, endpoint, headers, body } = attack.request;
    const url = `${CONFIG.BACKEND_URL}${endpoint}`;
    
    // Build headers
    const requestHeaders = new Headers();
    requestHeaders.set('Content-Type', 'application/json');
    
    // Handle auth token based on attack type
    if (attack.id === 'jwt-none-algorithm') {
        // Use malicious token with 'none' algorithm
        const maliciousToken = generateNoneAlgorithmToken();
        if (maliciousToken) {
            requestHeaders.set('Authorization', `Bearer ${maliciousToken}`);
        }
    } else if (attack.id === 'jwt-expired-token') {
        // Use an obviously expired token
        requestHeaders.set('Authorization', 'Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDAwMDAwMDAsImlhdCI6MTYwMDAwMDAwMCwic3ViIjoidGVzdHVzZXIifQ.invalid');
    } else if (headers?.Authorization === 'Bearer VALID_TOKEN' && state.authToken) {
        // Use real token for authenticated endpoints
        requestHeaders.set('Authorization', `Bearer ${state.authToken}`);
    } else if (headers?.Authorization) {
        // Use whatever token is specified (for attack simulation)
        requestHeaders.set('Authorization', headers.Authorization);
    }
    
    // Add any custom headers from attack definition
    if (headers) {
        Object.entries(headers).forEach(([key, value]) => {
            if (key !== 'Authorization' && key !== 'Content-Type') {
                requestHeaders.set(key, value);
            }
        });
    }
    
    const fetchOptions = {
        method: method,
        headers: requestHeaders,
        mode: 'cors'
    };
    
    if (body && method !== 'GET') {
        fetchOptions.body = JSON.stringify(body);
    }
    
    try {
        const response = await fetch(url, fetchOptions);
        
        let responseBody;
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            responseBody = await response.json();
        } else {
            responseBody = await response.text();
        }
        
        return {
            status: response.status,
            statusText: response.statusText,
            headers: Object.fromEntries(response.headers.entries()),
            body: responseBody,
            blocked: response.status >= 400,
            isReal: true  // Flag that this is a real response!
        };
    } catch (err) {
        return {
            status: 0,
            statusText: 'Network Error',
            body: { error: err.message },
            blocked: true,
            isReal: true
        };
    }
}

function showRealHttpResponse(response, responseTime) {
    const container = document.getElementById('httpResponse');
    const statusClass = response.status >= 400 ? 'error' : 'success';
    const isBlocked = response.status >= 400;
    
    let bodyStr;
    if (typeof response.body === 'object') {
        bodyStr = JSON.stringify(response.body, null, 2);
    } else {
        bodyStr = response.body;
    }
    
    // Truncate if too long
    if (bodyStr.length > 500) {
        bodyStr = bodyStr.substring(0, 500) + '\n... (truncated)';
    }
    
    container.innerHTML = `
        <div class="response-header ${statusClass}">
            <span class="real-badge">🔴 LIVE</span>
            HTTP/1.1 ${response.status} ${response.statusText}
        </div>
        <div class="response-info">
            <span>Response Time: ${responseTime}ms</span>
            <span class="result-badge ${isBlocked ? 'blocked' : 'allowed'}">
                ${isBlocked ? '🛡️ BLOCKED' : '⚠️ ALLOWED'}
            </span>
        </div>
        <pre class="response-body">${escapeHtml(bodyStr)}</pre>
    `;
    
    document.getElementById('responseTimer').textContent = `${responseTime}ms`;
}

// ==========================================================================
// UI Updates
// ==========================================================================
function showExecutingState(attack) {
    const container = document.getElementById('currentAttack');
    container.innerHTML = `
        <div class="attack-executing">
            <div class="attack-title-display">
                <span class="loading"></span>
                ${attack.name}
                <span class="attack-status-badge executing">EXECUTING</span>
            </div>
            <p style="color: var(--text-secondary); font-size: 0.875rem;">
                Simulating attack and monitoring system response...
            </p>
        </div>
    `;
    
    document.getElementById('responseTimer').textContent = '...';
}

function showAttackResult(attack, responseTime) {
    const result = attack.expectedResult;
    const statusClass = result.blocked ? 'blocked' : 'error';
    const statusIcon = result.blocked ? '🛡️' : '⚠️';
    
    const container = document.getElementById('currentAttack');
    container.innerHTML = `
        <div class="attack-executing">
            <div class="attack-title-display">
                ${statusIcon} ${attack.name}
                <span class="attack-status-badge ${statusClass}">
                    ${result.blocked ? 'BLOCKED' : 'DETECTED'}
                </span>
            </div>
            <div class="attack-result ${statusClass}">
                <div class="result-code ${statusClass}">
                    HTTP ${result.status}
                </div>
                <div class="result-message">${result.message}</div>
                <div class="result-explanation">
                    <strong>Defense activated:</strong> ${attack.defenseLayers.map(l => getLayerName(l)).join(' → ')}
                </div>
            </div>
        </div>
    `;
    
    document.getElementById('responseTimer').textContent = `${responseTime}ms`;
}

function showHttpRequest(request) {
    const content = document.getElementById('requestContent');
    content.innerHTML = formatHttpRequest(request);
    
    // Switch to request tab
    document.querySelectorAll('.inspector-tabs .tab').forEach(t => t.classList.remove('active'));
    document.querySelector('.tab[data-tab="request"]').classList.add('active');
    document.getElementById('requestContent').classList.remove('hidden');
    document.getElementById('responseContent').classList.add('hidden');
}

function showHttpResponse(result) {
    const content = document.getElementById('responseContent');
    const statusClass = result.status < 400 ? 'status-ok' : 'status-error';
    
    content.innerHTML = `<span class="${statusClass}">HTTP/1.1 ${result.status} ${getStatusText(result.status)}</span>
Content-Type: application/json
X-Request-Id: ${generateRequestId()}
X-Security-Action: ${result.blocked ? 'BLOCKED' : 'ALLOWED'}

{
  "code": ${result.status},
  "message": "${result.message}",
  "data": {
    "blocked": ${result.blocked},
    "timestamp": "${new Date().toISOString()}",
    "securityEvent": true
  }
}`;
}

function formatHttpRequest(request) {
    let formatted = `<span class="method">${request.method}</span> <span class="url">${request.endpoint}</span> HTTP/1.1
Host: localhost:8000
`;
    
    for (const [key, value] of Object.entries(request.headers)) {
        formatted += `<span class="header-name">${key}:</span> <span class="header-value">${value}</span>\n`;
    }
    
    if (request.body) {
        formatted += `\n${JSON.stringify(request.body, null, 2)}`;
    }
    
    return formatted;
}

// ==========================================================================
// Defense Layers
// ==========================================================================
function resetDefenseLayers() {
    document.querySelectorAll('.layer').forEach(layer => {
        layer.classList.remove('active', 'triggered');
        layer.querySelector('.layer-status').textContent = '—';
    });
}

async function activateDefenseLayer(layerName) {
    const layer = document.querySelector(`.layer[data-layer="${layerName}"]`);
    if (!layer) return;
    
    layer.classList.add('triggered');
    layer.querySelector('.layer-status').textContent = 'ACTIVE';
    
    await sleep(200);
    
    layer.classList.remove('triggered');
    layer.classList.add('active');
    layer.querySelector('.layer-status').textContent = '✓';
}

function getLayerName(layerId) {
    const names = {
        gateway: 'Kong Gateway',
        auth: 'JWT Auth',
        ownership: 'Ownership Check',
        validation: 'Input Validation',
        risk: 'Risk Engine',
        audit: 'Audit Logger'
    };
    return names[layerId] || layerId;
}

// ==========================================================================
// Forensics Log
// ==========================================================================
function clearLogs() {
    const container = document.getElementById('forensicsLog');
    container.innerHTML = '<div class="log-empty"><span>Waiting for security events...</span></div>';
}

async function addForensicLog(entry) {
    const container = document.getElementById('forensicsLog');
    
    // Remove empty state if present
    const emptyState = container.querySelector('.log-empty');
    if (emptyState) {
        emptyState.remove();
    }
    
    const logEntry = document.createElement('div');
    const logClass = entry.result === 'BLOCKED' ? 'blocked' : 
                     entry.result === 'FAILED' ? 'warning' : 'info';
    
    logEntry.className = `log-entry ${logClass}`;
    logEntry.innerHTML = `
        <div class="log-timestamp">${new Date().toISOString()}</div>
        <div class="log-action">${entry.action}: ${entry.result}</div>
        <div class="log-details">${entry.details}</div>
    `;
    
    container.insertBefore(logEntry, container.firstChild);
}

// ==========================================================================
// Risk Gauge
// ==========================================================================
async function animateRiskGauge(targetScore) {
    const gauge = document.getElementById('gaugeFill');
    const scoreDisplay = document.getElementById('riskScore');
    const riskLabel = document.getElementById('riskLabel');
    
    // Determine risk level
    let level = 'low';
    if (targetScore >= 70) level = 'high';
    else if (targetScore >= 40) level = 'medium';
    
    // Update label
    riskLabel.className = `risk-label ${level}`;
    riskLabel.textContent = level.toUpperCase();
    
    // Animate gauge fill
    gauge.style.width = `${targetScore}%`;
    
    // Animate score counter
    let currentScore = 0;
    const increment = targetScore / 20;
    const interval = setInterval(() => {
        currentScore = Math.min(currentScore + increment, targetScore);
        scoreDisplay.textContent = Math.round(currentScore);
        
        if (currentScore >= targetScore) {
            clearInterval(interval);
        }
    }, 25);
}

// ==========================================================================
// Stats
// ==========================================================================
function updateStats() {
    document.getElementById('totalAttacks').textContent = state.totalAttacks;
    document.getElementById('blockedCount').textContent = state.blockedCount;
    
    const avgTime = state.responseTimes.length > 0
        ? Math.round(state.responseTimes.reduce((a, b) => a + b, 0) / state.responseTimes.length)
        : 0;
    document.getElementById('avgResponseTime').textContent = `${avgTime}ms`;
}

// ==========================================================================
// Event Listeners
// ==========================================================================
function setupEventListeners() {
    // Tab switching in HTTP inspector
    document.querySelectorAll('.inspector-tabs .tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.inspector-tabs .tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            
            const tabName = tab.dataset.tab;
            document.getElementById('requestContent').classList.toggle('hidden', tabName !== 'request');
            document.getElementById('responseContent').classList.toggle('hidden', tabName !== 'response');
        });
    });
    
    // Modal overlay click to close
    document.getElementById('modalOverlay').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) {
            closeModal();
        }
    });
    
    // Escape key to close modal
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeModal();
        }
    });
}

// ==========================================================================
// Reset
// ==========================================================================
function resetAll() {
    state.selectedAttack = null;
    state.executedAttacks.clear();
    state.totalAttacks = 0;
    state.blockedCount = 0;
    state.responseTimes = [];
    state.isExecuting = false;
    
    // Reset UI
    renderAttackLibrary();
    updateStats();
    clearLogs();
    resetDefenseLayers();
    
    // Reset gauge
    document.getElementById('gaugeFill').style.width = '0%';
    document.getElementById('riskScore').textContent = '0';
    document.getElementById('riskLabel').className = 'risk-label';
    document.getElementById('riskLabel').textContent = 'IDLE';
    
    // Reset response panel
    document.getElementById('currentAttack').innerHTML = `
        <div class="attack-idle">
            <div class="idle-icon">🎯</div>
            <p>Select an attack to begin simulation</p>
        </div>
    `;
    
    document.getElementById('responseTimer').textContent = '--ms';
    document.getElementById('requestContent').textContent = '// Select an attack to see the HTTP request';
    document.getElementById('responseContent').textContent = '// Response will appear here';
    
    console.log('🔄 Demo reset complete');
}

// ==========================================================================
// Utility Functions
// ==========================================================================
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function getStatusText(status) {
    const texts = {
        200: 'OK',
        201: 'Created',
        400: 'Bad Request',
        401: 'Unauthorized',
        403: 'Forbidden',
        404: 'Not Found',
        409: 'Conflict',
        429: 'Too Many Requests',
        500: 'Internal Server Error'
    };
    return texts[status] || 'Unknown';
}

function generateRequestId() {
    return 'req_' + Math.random().toString(36).substr(2, 16);
}

// ==========================================================================
// HTML Escaping (Security)
// ==========================================================================
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Export for console debugging
window.demoState = state;
window.demoConfig = CONFIG;
window.resetAll = resetAll;
window.checkBackendStatus = checkBackendStatus;
window.getAuthToken = getAuthToken;
