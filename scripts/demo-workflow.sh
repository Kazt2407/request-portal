#!/usr/bin/env bash
# Walks one request through the whole workflow, as three different users, against a running stack:
# docker compose infrastructure + org-service + request-service + notification-service + gateway.
# Ends by listing the emails the workflow produced. Needs curl and jq.
set -euo pipefail
cd "$(dirname "$0")/.."

api="${API_URL:-http://localhost:8080/api/v1}"

student=$(scripts/token.sh student)
academic=$(scripts/token.sh staff.academic)
finance=$(scripts/token.sh staff.finance)

call() {
  local token=$1
  shift
  curl -sS --fail-with-body -H "Authorization: Bearer $token" -H 'Content-Type: application/json' "$@"
}
heading() { printf '\n\033[1m%s\033[0m\n' "$*"; }
new_uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen | tr '[:upper:]' '[:lower:]'; }

heading "1. Student Nguyễn Văn An: what can I request?"
templates=$(call "$student" "$api/templates/available")
echo "$templates" | jq -r '.[] | "   - \(.title): \(.stepCount) step(s), up to \(.totalSlaDays) day(s)"'
template_id=$(echo "$templates" | jq -r '.[] | select(.title == "Official transcript") | .id')

heading "2. Student submits an official transcript request, then retries with the same Idempotency-Key"
key=$(new_uuid)
body=$(jq -n --argjson template "$template_id" \
  '{templateId: $template, values: {copies: "2", language: "English", reason: "Exchange programme application"}}')
request=$(call "$student" -X POST -H "Idempotency-Key: $key" -d "$body" "$api/requests")
retry=$(call "$student" -X POST -H "Idempotency-Key: $key" -d "$body" "$api/requests")
id=$(echo "$request" | jq -r .id)
echo "   created $(echo "$request" | jq -r .code) (id $id); the retry returned id $(echo "$retry" | jq -r .id)"

heading "3. Academic Affairs (Lê Văn Cường): what is waiting for my department?"
call "$academic" "$api/requests?awaitingMe=true&size=5" \
  | jq -r '.items[] | "   - \(.code) \(.title) from \(.requester.name), due \(.currentStep.dueAt)"'

heading "4. Academic Affairs completes step 1, attaching the scanned transcript the step requires"
call "$academic" -X POST \
  -d "{\"note\": \"Signed and stamped\", \"attachmentIds\": [\"$(new_uuid)\"]}" \
  "$api/requests/$id/steps/1/complete" \
  | jq -r '"   now at step \(.currentStep): \(.steps[1].description) (\(.steps[1].departmentCode))"'

heading "5. Finance (Phạm Thị Dung) completes step 2"
call "$finance" -X POST -d '{"note": "Fee paid at the counter"}' "$api/requests/$id/steps/2/complete" \
  | jq -r '"   request status: \(.status)"'

heading "6. Finance records that the student collected the transcript"
call "$finance" -X PUT -d '{"received": true}' "$api/requests/$id/result-received" \
  | jq -r '"   result received: \(.resultReceived)"'

heading "7. Student: the request's history"
call "$student" "$api/requests/$id" | jq -r '.history[]
  | "   \(.at[0:19])  \(.action)\(if .stepPosition then " (step \(.stepPosition))" else "" end) by \(.actor.name)\(if .note then " — \(.note)" else "" end)"'

heading "8. Emails, delivered through Kafka and notification-service (Mailpit inbox: http://localhost:8025)"
code=$(echo "$request" | jq -r .code)
mailpit="${MAILPIT_URL:-http://localhost:8025}/api/v1"
for _ in $(seq 1 30); do
  emails=$(curl -sS -G "$mailpit/search" --data-urlencode "query=subject:\"[$code]\"")
  [ "$(echo "$emails" | jq '.messages_count')" -ge 4 ] && break
  sleep 1
done
echo "$emails" | jq -r '.messages | sort_by(.Created)[] | "   to \(.To[0].Address): \(.Subject)"'
