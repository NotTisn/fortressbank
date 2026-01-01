#!/bin/bash
# Keycloak Authentication Helper
# Gets access token for testing

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8888}"
REALM="${REALM:-fortressbank-realm}"
CLIENT_ID="${CLIENT_ID:-kong}"
CLIENT_SECRET="${CLIENT_SECRET:-pmFStZwGO8sb0mBDkZmP5niE3wmEELqe}"

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <username> <password>"
    echo ""
    echo "Example:"
    echo "  $0 testuser password123"
    echo ""
    echo "Environment variables:"
    echo "  KEYCLOAK_URL (default: http://localhost:8888)"
    echo "  REALM (default: fortressbank-realm)"
    echo "  CLIENT_ID (default: kong)"
    exit 1
fi

USERNAME="$1"
PASSWORD="$2"

echo "🔐 Authenticating with Keycloak..."
echo "   Realm: $REALM"
echo "   User: $USERNAME"

# Get token
RESPONSE=$(curl -s -X POST \
    "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "username=$USERNAME" \
    -d "password=$PASSWORD" \
    -d "grant_type=password")

# Check for error
if echo "$RESPONSE" | grep -q "error"; then
    echo "❌ Authentication failed:"
    echo "$RESPONSE" | jq '.'
    exit 1
fi

# Extract tokens
ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "$RESPONSE" | jq -r '.refresh_token')
EXPIRES_IN=$(echo "$RESPONSE" | jq -r '.expires_in')

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ Failed to extract access token"
    echo "$RESPONSE"
    exit 1
fi

echo "✅ Authentication successful"
echo "   Expires in: ${EXPIRES_IN}s"
echo ""

# Decode token to get user info (without verification)
echo "📋 Token Claims:"
echo "$ACCESS_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq '.' || echo "(could not decode)"

echo ""
echo "🎫 ACCESS_TOKEN="
echo "$ACCESS_TOKEN"
echo ""
echo "🔄 REFRESH_TOKEN="
echo "$REFRESH_TOKEN"
echo ""

# Export for use in other scripts
export ACCESS_TOKEN
export REFRESH_TOKEN

# Save to temp file for easy sourcing
cat > /tmp/fortressbank-tokens.sh <<EOF
export ACCESS_TOKEN="$ACCESS_TOKEN"
export REFRESH_TOKEN="$REFRESH_TOKEN"
export USERNAME="$USERNAME"
EOF

echo "💾 Tokens saved to /tmp/fortressbank-tokens.sh"
echo "   Source it: source /tmp/fortressbank-tokens.sh"
