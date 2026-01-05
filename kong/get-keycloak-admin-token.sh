#!/bin/bash
# Script to get Keycloak admin token for Kong single-device plugin
# This uses the backend-service service account

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8888}"
REALM="${REALM:-fortressbank-realm}"
CLIENT_ID="${CLIENT_ID:-backend-service}"
CLIENT_SECRET="${CLIENT_SECRET:-kFPdo6z77a80FkK9v8CcS8fOzYrVepkg}"

echo "Getting admin token from Keycloak..."
echo "URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo "Client: $CLIENT_ID"
echo ""

TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET")

# Check if jq is available
if command -v jq &> /dev/null; then
  ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
  if [ "$ACCESS_TOKEN" != "null" ] && [ -n "$ACCESS_TOKEN" ]; then
    echo "✅ Success! Access token:"
    echo "$ACCESS_TOKEN"
    echo ""
    echo "To use this token, set it as an environment variable:"
    echo "export KONG_KEYCLOAK_ADMIN_TOKEN=\"$ACCESS_TOKEN\""
    echo ""
    echo "Or add it directly to kong.yml:"
    echo "admin_token: \"$ACCESS_TOKEN\""
  else
    echo "❌ Error: Failed to get access token"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
  fi
else
  # Fallback if jq is not available
  echo "⚠️  jq not found. Full response:"
  echo "$TOKEN_RESPONSE"
  echo ""
  echo "Install jq to extract the token automatically, or manually extract 'access_token' from the JSON above."
fi

