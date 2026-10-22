#!/usr/bin/env bash
# Prints an access token for a local test user (see "Test users" in the README).
# Example: curl -H "Authorization: Bearer $(scripts/token.sh admin)" localhost:8080/api/v1/accounts/me
set -euo pipefail

username="${1:?usage: scripts/token.sh <username>}"
keycloak="${KEYCLOAK_URL:-http://localhost:8180}"

curl -fsS "$keycloak/realms/request-portal/protocol/openid-connect/token" \
  -d grant_type=password \
  -d client_id=portal-dev \
  --data-urlencode "username=$username" \
  --data-urlencode "password=${PASSWORD:-password}" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
