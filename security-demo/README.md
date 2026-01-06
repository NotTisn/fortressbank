# 🛡️ FortressBank Security Incident Simulator

A **visual, interactive security demo dashboard** that executes REAL attacks against the running backend and shows REAL responses.

## ⚠️ IMPORTANT: Real vs Simulation Mode

| Mode | What happens | Status Badge |
|------|--------------|--------------|
| **LIVE** | Real HTTP calls to Kong Gateway | 🔴 LIVE + "Backend LIVE" |
| **Simulation** | Fake responses (for UI demo only) | "Simulation Mode" |

**The professor can poke:** When backend is running, every attack makes a REAL HTTP request. The responses are REAL. You can verify in browser DevTools → Network tab.

## 🚀 Quick Start

### Step 1: Start the Backend (REQUIRED for real attacks!)

```powershell
cd fortressbank_be/infrastructure
.\dev.bat -infra      # Start Docker infrastructure
.\dev.bat             # Start all Java services
```

Wait for all services to be healthy (2-3 minutes).

### Step 2: Start the Demo Dashboard

```powershell
cd fortressbank_be/security-demo
.\serve.bat
```

Then open: **http://localhost:3000**

### Step 3: Verify LIVE mode

Look at the header:
- ✅ **"Backend LIVE"** with red dot = REAL attacks!
- ⚠️ **"Simulation Mode"** = Backend not running, fake responses

## 🔍 How to PROVE It's Real (For Skeptical Professors)

### Method 1: Browser DevTools

1. Open dashboard in Chrome
2. Press `F12` → Network tab
3. Click an attack
4. **See the actual HTTP request go out**
5. See the real response come back

### Method 2: Check Kong Logs

```powershell
docker logs fortressbank-kong -f
```

When you click an attack, you'll see the request in Kong's access log!

### Method 3: Check Backend Logs

```powershell
cd fortressbank_be/infrastructure
.\dev.bat -logs
```

Open the service log for the endpoint being attacked. You'll see the real error being thrown.

### Method 4: Correlation IDs

Every request has a unique `X-Request-Id` header. You can trace it through:
- Kong logs
- Backend service logs
- Audit database

## 🎯 What Makes This REAL

### Attack Scenarios (11 total)

| # | Attack | OWASP | Severity |
|---|--------|-------|----------|
| 1 | IDOR: Account Takeover | A01:2021 | 🔴 Critical |
| 2 | Role Escalation | A01:2021 | 🔴 Critical |
| 3 | JWT "none" Algorithm | A02:2021 | 🔴 Critical |
| 4 | SQL Injection | A03:2021 | 🔴 Critical |
| 5 | Negative Fee Exploit | A04:2021 | 🟠 High |
| 6 | Double-Spend Race | A04:2021 | 🔴 Critical |
| 7 | Brute Force Login | A07:2021 | 🟠 High |
| 8 | Expired Token Reuse | A07:2021 | 🟠 High |
| 9 | Internal API Exposure | A05:2021 | 🔴 Critical |
| 10 | Smart OTP Brute Force | A07:2021 | 🟠 High |
| 11 | Device Fingerprint Spoof | A07:2021 | 🟠 High |

### Defense Layers Visualized

1. 🚪 **Kong Gateway** — Rate limiting, route protection
2. 🔐 **JWT Auth** — Token validation, signature verification
3. 👤 **Ownership Check** — IDOR prevention
4. ✅ **Input Validation** — Bean validation, sanitization
5. 📊 **Risk Engine** — Fraud detection scoring
6. 📝 **Audit Logger** — Forensic trail capture

## 🎮 How to Demo

### The 3-Minute Security Story

**Opening (30 sec):**
> "FortressBank processes millions in transactions daily. Attackers constantly probe for weaknesses. Watch how our defense-in-depth architecture responds to real attack patterns."

**Demo Flow (2 min):**

1. **Click "IDOR: Account Takeover"**
   - Watch the attack execute
   - See HTTP request/response
   - Watch defense layers light up
   - See risk gauge spike to 85%
   - Show forensic trail entries

2. **Click "JWT none Algorithm"**
   - Classic cryptographic attack
   - System rejects in milliseconds
   - Explain: "Even if they forge the token structure, signature validation catches it"

3. **Click "Negative Fee Exploit"**
   - Business logic attack
   - Show how validation + risk engine work together

**Closing (30 sec):**
> "Every attack is logged. Every anomaly is scored. Every transaction leaves a forensic trail. This is defense-in-depth in action."

## 🎨 UI Components

### Attack Library (Left Panel)
- Click any attack card to see details
- Cards show severity, OWASP category
- Executed attacks are marked ✓

### Live Response (Center Panel)
- Real-time HTTP request/response
- Response time measurement
- Result status (BLOCKED/DETECTED)

### Risk Gauge
- Visual threat level indicator
- Animates from 0-100
- Color-coded: Green → Yellow → Red

### Forensic Trail (Right Panel)
- Audit log entries stream in real-time
- Each entry shows timestamp, action, result
- Defense layers light up as activated

## ⚙️ Configuration

Edit `app.js` to configure:

```javascript
const CONFIG = {
    // Backend URL for real attack execution
    BACKEND_URL: 'http://localhost:8000',
    
    // true = simulated, false = real HTTP calls
    SIMULATION_MODE: true,
    
    // Animation timings (ms)
    TYPING_SPEED: 30,
    LAYER_ACTIVATION_DELAY: 300,
    LOG_ENTRY_DELAY: 400
};
```

## 🔌 Real Backend Integration

To connect to the actual FortressBank backend:

1. Ensure backend is running (`dev.bat -infra && dev.bat`)
2. Set `CONFIG.SIMULATION_MODE = false`
3. Configure `CONFIG.BACKEND_URL`
4. Attacks will make real HTTP requests

> ⚠️ **Note:** Some attacks may require valid JWT tokens. The simulation mode is recommended for demos.

## 🗂️ File Structure

```
security-demo/
├── index.html      # Main dashboard HTML
├── styles.css      # Professional dark theme styling
├── attacks.js      # Attack scenario definitions
├── app.js          # Application logic
└── README.md       # This file
```

## 🎯 Adding New Attacks

To add a new attack scenario, edit `attacks.js`:

```javascript
{
    id: 'my-new-attack',
    name: 'Attack Display Name',
    category: 'Access Control', // or other category
    severity: 'critical',       // critical, high, medium
    owasp: 'A01:2021',
    description: 'What the attack does...',
    technicalDetails: 'How it works technically...',
    expectedResult: {
        status: 403,
        message: 'Error message shown',
        blocked: true
    },
    defenseLayers: ['gateway', 'auth', 'ownership'],
    riskScore: 85,
    request: {
        method: 'POST',
        endpoint: '/api/endpoint',
        headers: { ... },
        body: { ... }
    },
    auditTrail: [
        { action: 'ACTION', result: 'RESULT', details: 'Details' }
    ]
}
```

## 🎓 Educational Value

This demo teaches:

1. **Attack Patterns** — How real attacks work
2. **Defense Layers** — Multiple security boundaries
3. **Observability** — Audit trails and forensics
4. **Risk Scoring** — Threat quantification
5. **OWASP Mapping** — Industry-standard classification

## 📸 Screenshots

The dashboard features:
- Dark mode professional styling
- Animated risk gauge
- Real-time log streaming
- Responsive layout

## 🐛 Troubleshooting

**Page is blank:**
- Check browser console for errors
- Ensure all 4 files are in the same folder

**Attacks don't execute:**
- Check that `attacks.js` loads before `app.js`
- Look for JavaScript errors in console

**Styling broken:**
- Verify `styles.css` is in the same folder
- Check for CSS parsing errors

## 📝 License

Internal use only — FortressBank Security Team

---

**Built for FortressBank Demo Day** 🏦🛡️
