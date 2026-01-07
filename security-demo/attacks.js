/**
 * FortressBank Security Demo - Attack Definitions
 * 
 * THESE ARE REAL ATTACKS that execute against the running backend!
 * Each attack makes actual HTTP calls to test the security controls.
 */

const ATTACKS = [
    // ==========================================================================
    // OWASP A01:2021 - Broken Access Control
    // ==========================================================================
    {
        id: 'idor-transfer',
        name: 'IDOR: Account Takeover',
        category: 'Access Control',
        severity: 'critical',
        owasp: 'A01:2021',
        description: 'Attempt to access another user\'s account by manipulating the account ID.',
        technicalDetails: 'The attacker changes the account ID to a victim\'s account, hoping the backend doesn\'t verify ownership.',
        expectedResult: {
            status: 403,
            message: 'Account ownership verification failed',
            blocked: true
        },
        defenseLayers: ['gateway', 'auth', 'ownership', 'audit'],
        riskScore: 85,
        request: {
            method: 'GET',
            endpoint: '/accounts/00000000-0000-0000-0000-000000000001',  // Non-existent account
            headers: {
                'Authorization': 'Bearer VALID_TOKEN',
                'Content-Type': 'application/json'
            },
            body: null
        },
        auditTrail: [
            { action: 'TOKEN_VALIDATED', result: 'SUCCESS', details: 'JWT signature verified' },
            { action: 'OWNERSHIP_CHECK', result: 'FAILED', details: 'User does not own this account' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'Access denied - ownership mismatch' },
            { action: 'SECURITY_EVENT', result: 'LOGGED', details: 'Potential IDOR attack detected' }
        ]
    },
    
    {
        id: 'privilege-escalation',
        name: 'Role Escalation Attack',
        category: 'Access Control',
        severity: 'critical',
        owasp: 'A01:2021',
        description: 'Attempt to access admin-only endpoints with a regular user token.',
        technicalDetails: 'The attacker tries to call admin endpoints hoping role-based access control is missing.',
        expectedResult: {
            status: 403,
            message: 'Insufficient privileges',
            blocked: true
        },
        defenseLayers: ['gateway', 'auth', 'audit'],
        riskScore: 90,
        request: {
            method: 'GET',
            endpoint: '/admin/users',  // Real admin endpoint
            headers: {
                'Authorization': 'Bearer VALID_TOKEN',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: null
        },
        auditTrail: [
            { action: 'TOKEN_VALIDATED', result: 'SUCCESS', details: 'JWT signature verified' },
            { action: 'ROLE_CHECK', result: 'FAILED', details: 'Required role: ADMIN, User role: USER' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'Privilege escalation attempt blocked' }
        ]
    },

    // ==========================================================================
    // OWASP A02:2021 - Cryptographic Failures
    // ==========================================================================
    {
        id: 'jwt-none-algorithm',
        name: 'JWT "none" Algorithm Attack',
        category: 'Cryptographic',
        severity: 'critical',
        owasp: 'A02:2021',
        description: 'Attempt to bypass JWT signature verification using the "none" algorithm.',
        technicalDetails: 'Attacker crafts a JWT with "alg": "none" hoping the server accepts unsigned tokens.',
        expectedResult: {
            status: 401,
            message: 'Invalid token signature',
            blocked: true
        },
        defenseLayers: ['gateway', 'auth'],
        riskScore: 95,
        request: {
            method: 'GET',
            endpoint: '/accounts/my-accounts',  // Real protected endpoint
            headers: {
                'Authorization': 'Bearer eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJhZG1pbiJ9.',
                'Content-Type': 'application/json'
            },
            body: null
        },
        auditTrail: [
            { action: 'TOKEN_PARSE', result: 'SUCCESS', details: 'Token structure valid' },
            { action: 'ALGORITHM_CHECK', result: 'FAILED', details: 'Algorithm "none" is not allowed' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'JWT algorithm attack detected' }
        ]
    },

    // ==========================================================================
    // OWASP A03:2021 - Injection
    // ==========================================================================
    {
        id: 'sql-injection',
        name: 'SQL Injection Attempt',
        category: 'Injection',
        severity: 'critical',
        owasp: 'A03:2021',
        description: 'Attempt to inject SQL commands through the account search parameter.',
        technicalDetails: 'Attacker injects SQL in the query parameter hoping for direct SQL execution.',
        expectedResult: {
            status: 400,
            message: 'Invalid input format',
            blocked: true
        },
        defenseLayers: ['gateway', 'validation', 'audit'],
        riskScore: 88,
        request: {
            method: 'GET',
            endpoint: '/accounts/search?query=\'; DROP TABLE accounts; --',
            headers: {
                'Authorization': 'Bearer <valid_token>',
                'Content-Type': 'application/json'
            },
            body: null
        },
        auditTrail: [
            { action: 'INPUT_VALIDATION', result: 'FAILED', details: 'Malicious pattern detected in query parameter' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'SQL injection attempt blocked' },
            { action: 'SECURITY_EVENT', result: 'LOGGED', details: 'Injection attack pattern detected' }
        ]
    },

    // ==========================================================================
    // OWASP A04:2021 - Insecure Design
    // ==========================================================================
    {
        id: 'fee-manipulation',
        name: 'Negative Fee Exploitation',
        category: 'Business Logic',
        severity: 'high',
        owasp: 'A04:2021',
        description: 'Attempt to set a negative transaction fee to gain money instead of paying fees.',
        technicalDetails: 'Attacker manipulates the fee field hoping to credit their account with the "negative fee".',
        expectedResult: {
            status: 400,
            message: 'Fee must be non-negative',
            blocked: true
        },
        defenseLayers: ['validation', 'risk', 'audit'],
        riskScore: 75,
        request: {
            method: 'POST',
            endpoint: '/transactions/transfers/initiate',
            headers: {
                'Authorization': 'Bearer <valid_token>',
                'Content-Type': 'application/json'
            },
            body: {
                senderAccountId: 'MY-ACCOUNT-123',
                receiverAccountNumber: 'RECIPIENT-456',
                amount: 100,
                fee: -50,
                description: 'Fee manipulation attempt'
            }
        },
        auditTrail: [
            { action: 'INPUT_VALIDATION', result: 'FAILED', details: 'fee: must be greater than or equal to 0' },
            { action: 'RISK_ENGINE', result: 'TRIGGERED', details: 'Anomalous fee value detected, score +30' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'Business logic violation' }
        ]
    },

    {
        id: 'double-spend',
        name: 'Race Condition Double-Spend',
        category: 'Business Logic',
        severity: 'critical',
        owasp: 'A04:2021',
        description: 'Attempt to exploit race conditions by sending multiple transfers simultaneously.',
        technicalDetails: 'Attacker sends 10 identical transfer requests in parallel, hoping some bypass balance checks.',
        expectedResult: {
            status: 409,
            message: 'Optimistic locking conflict',
            blocked: true
        },
        defenseLayers: ['validation', 'risk', 'audit'],
        riskScore: 92,
        request: {
            method: 'POST',
            endpoint: '/transactions/transfers/initiate',
            headers: {
                'Authorization': 'Bearer <valid_token>',
                'Content-Type': 'application/json'
            },
            body: {
                senderAccountId: 'MY-ACCOUNT-123',
                receiverAccountNumber: 'RECIPIENT-456',
                amount: 1000,
                description: 'Concurrent transfer #1-10'
            }
        },
        auditTrail: [
            { action: 'TRANSFER_INITIATED', result: 'SUCCESS', details: 'First transfer processed' },
            { action: 'CONCURRENT_CHECK', result: 'CONFLICT', details: 'Version mismatch on account balance' },
            { action: 'TRANSACTION_ROLLED_BACK', result: 'BLOCKED', details: '9 duplicate transfers rejected' },
            { action: 'RISK_ENGINE', result: 'ELEVATED', details: 'Velocity pattern detected, score +40' }
        ]
    },

    // ==========================================================================
    // OWASP A07:2021 - Identification and Authentication Failures
    // ==========================================================================
    {
        id: 'brute-force-login',
        name: 'Brute Force Login Attack',
        category: 'Authentication',
        severity: 'high',
        owasp: 'A07:2021',
        description: 'Attempt to guess user credentials through repeated login attempts.',
        technicalDetails: 'Attacker tries common passwords hoping to gain access to user accounts.',
        expectedResult: {
            status: 429,
            message: 'Account temporarily locked',
            blocked: true
        },
        defenseLayers: ['gateway', 'auth', 'audit'],
        riskScore: 80,
        request: {
            method: 'POST',
            endpoint: '/auth/login',
            headers: {
                'Content-Type': 'application/json'
            },
            body: {
                username: 'victim@bank.com',
                password: 'password123'
            }
        },
        auditTrail: [
            { action: 'LOGIN_ATTEMPT_1', result: 'FAILED', details: 'Invalid credentials' },
            { action: 'LOGIN_ATTEMPT_2', result: 'FAILED', details: 'Invalid credentials' },
            { action: 'LOGIN_ATTEMPT_3', result: 'FAILED', details: 'Invalid credentials' },
            { action: 'LOGIN_ATTEMPT_4', result: 'FAILED', details: 'Invalid credentials' },
            { action: 'LOGIN_ATTEMPT_5', result: 'FAILED', details: 'Invalid credentials' },
            { action: 'ACCOUNT_LOCKED', result: 'BLOCKED', details: 'Account locked for 15 minutes' },
            { action: 'SECURITY_ALERT', result: 'TRIGGERED', details: 'Brute force attack detected from IP 192.168.1.100' }
        ]
    },

    {
        id: 'expired-token-reuse',
        name: 'Expired Token Replay',
        category: 'Session',
        severity: 'high',
        owasp: 'A07:2021',
        description: 'Attempt to use an expired or revoked authentication token.',
        technicalDetails: 'Attacker tries to reuse a token after the user has logged out.',
        expectedResult: {
            status: 401,
            message: 'Token expired or revoked',
            blocked: true
        },
        defenseLayers: ['gateway', 'auth'],
        riskScore: 70,
        request: {
            method: 'GET',
            endpoint: '/accounts/balance',
            headers: {
                'Authorization': 'Bearer <expired_token_from_1_hour_ago>',
                'Content-Type': 'application/json'
            },
            body: null
        },
        auditTrail: [
            { action: 'TOKEN_VALIDATED', result: 'FAILED', details: 'Token expired at 2026-01-05T12:00:00Z' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'Expired token rejected' },
            { action: 'SESSION_CHECK', result: 'FAILED', details: 'Session already terminated' }
        ]
    },

    // ==========================================================================
    // OWASP A05:2021 - Security Misconfiguration
    // ==========================================================================
    {
        id: 'internal-endpoint-access',
        name: 'Internal API Exposure',
        category: 'Misconfiguration',
        severity: 'critical',
        owasp: 'A05:2021',
        description: 'Attempt to access internal-only endpoints from outside the trusted network.',
        technicalDetails: 'Attacker tries to call /internal/* endpoints that should only be accessible within the Docker network.',
        expectedResult: {
            status: 404,
            message: 'Route not found',
            blocked: true
        },
        defenseLayers: ['gateway', 'audit'],
        riskScore: 85,
        request: {
            method: 'POST',
            endpoint: '/accounts/internal/debit',
            headers: {
                'Authorization': 'Bearer <valid_token>',
                'Content-Type': 'application/json'
            },
            body: {
                accountId: 'ANY-ACCOUNT',
                amount: 10000
            }
        },
        auditTrail: [
            { action: 'ROUTE_LOOKUP', result: 'NOT_FOUND', details: 'Path /accounts/internal/debit not exposed via Kong' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'Internal endpoint not accessible externally' }
        ]
    },

    // ==========================================================================
    // Smart OTP Attacks
    // ==========================================================================
    {
        id: 'smart-otp-brute-force',
        name: 'Smart OTP Brute Force',
        category: 'MFA Bypass',
        severity: 'high',
        owasp: 'A07:2021',
        description: 'Attempt to brute-force the 6-digit Smart OTP verification code.',
        technicalDetails: 'Attacker tries multiple OTP codes hoping to guess the correct one.',
        expectedResult: {
            status: 429,
            message: 'Too many verification attempts',
            blocked: true
        },
        defenseLayers: ['validation', 'risk', 'audit'],
        riskScore: 78,
        request: {
            method: 'POST',
            endpoint: '/transactions/smart-otp/verify-device',
            headers: {
                'Authorization': 'Bearer <valid_token>',
                'Content-Type': 'application/json'
            },
            body: {
                challengeId: 'challenge-123',
                otpCode: '000001'
            }
        },
        auditTrail: [
            { action: 'OTP_VERIFY_1', result: 'FAILED', details: 'Invalid OTP code' },
            { action: 'OTP_VERIFY_2', result: 'FAILED', details: 'Invalid OTP code' },
            { action: 'OTP_VERIFY_3', result: 'FAILED', details: 'Invalid OTP code' },
            { action: 'CHALLENGE_INVALIDATED', result: 'BLOCKED', details: 'Max attempts exceeded, challenge voided' },
            { action: 'RISK_SCORE_ELEVATED', result: 'LOGGED', details: 'Brute force pattern, risk +25' }
        ]
    },

    {
        id: 'smart-otp-device-spoof',
        name: 'Device Fingerprint Spoofing',
        category: 'MFA Bypass',
        severity: 'high',
        owasp: 'A07:2021',
        description: 'Attempt to register a fake device to bypass Smart OTP challenge.',
        technicalDetails: 'Attacker provides a fabricated device fingerprint hoping to bypass device binding.',
        expectedResult: {
            status: 400,
            message: 'Device verification failed',
            blocked: true
        },
        defenseLayers: ['validation', 'risk', 'audit'],
        riskScore: 72,
        request: {
            method: 'POST',
            endpoint: '/transactions/smart-otp/register-device',
            headers: {
                'Authorization': 'Bearer <valid_token>',
                'Content-Type': 'application/json'
            },
            body: {
                deviceId: 'SPOOFED-DEVICE-12345',
                publicKey: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...',
                deviceName: 'Fake iPhone 99'
            }
        },
        auditTrail: [
            { action: 'DEVICE_REGISTRATION', result: 'STARTED', details: 'New device registration request' },
            { action: 'FINGERPRINT_VALIDATION', result: 'FAILED', details: 'Device fingerprint format invalid' },
            { action: 'REQUEST_BLOCKED', result: 'BLOCKED', details: 'Suspicious device registration rejected' },
            { action: 'RISK_ENGINE', result: 'ELEVATED', details: 'New device from unknown location, score +20' }
        ]
    }
];

// Attack category colors and icons
const CATEGORY_CONFIG = {
    'Access Control': { color: '#FF4267', icon: '🔓' },
    'Cryptographic': { color: '#FFAF2A', icon: '🔐' },
    'Injection': { color: '#FF4267', icon: '💉' },
    'Business Logic': { color: '#0890FE', icon: '⚙️' },
    'Authentication': { color: '#FFAF2A', icon: '🔑' },
    'Session': { color: '#0890FE', icon: '🎫' },
    'Misconfiguration': { color: '#FF4267', icon: '⚠️' },
    'MFA Bypass': { color: '#FFAF2A', icon: '📱' }
};

// OWASP mapping for educational display
const OWASP_MAP = {
    'A01:2021': 'Broken Access Control',
    'A02:2021': 'Cryptographic Failures',
    'A03:2021': 'Injection',
    'A04:2021': 'Insecure Design',
    'A05:2021': 'Security Misconfiguration',
    'A07:2021': 'Auth Failures'
};

// Export for use in app.js
if (typeof window !== 'undefined') {
    window.ATTACKS = ATTACKS;
    window.CATEGORY_CONFIG = CATEGORY_CONFIG;
    window.OWASP_MAP = OWASP_MAP;
}
