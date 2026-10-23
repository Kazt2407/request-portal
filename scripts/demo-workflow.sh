#!/usr/bin/env bash
# Walks one request through the whole workflow, as three different users, against a running stack:
# docker compose infrastructure + all services + gateway. Files go straight to S3 storage through
# pre-signed URLs. Ends by listing the emails the workflow produced. Needs curl, jq and python3.
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
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Uploads a PDF the way a browser does: ask for an upload URL, PUT the bytes to storage, confirm. Prints the id.
upload() {
  local token=$1 name=$2 purpose=$3 file=$4 ticket
  ticket=$(call "$token" -X POST "$api/files" -d "$(jq -n --arg name "$name" --arg purpose "$purpose" \
    --argjson size "$(wc -c < "$file")" '{fileName: $name, contentType: "application/pdf", size: $size, purpose: $purpose}')")
  curl -sS --fail -X PUT -H 'Content-Type: application/pdf' --data-binary "@$file" "$(echo "$ticket" | jq -r .uploadUrl)"
  call "$token" -X POST "$api/files/$(echo "$ticket" | jq -r .fileId)/complete" | jq -r .id
}

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

heading "4. Academic Affairs uploads the stamped transcript and completes step 1, which requires it"
printf '%%PDF-1.7\n%% Official transcript of Nguyễn Văn An, signed and stamped\n' > "$work/scan.pdf"
scan=$(upload "$academic" "Bảng điểm - đã đóng dấu.pdf" STEP_ATTACHMENT "$work/scan.pdf")
echo "   uploaded file $scan straight to storage"
call "$academic" -X POST \
  -d "{\"note\": \"Signed and stamped\", \"attachmentIds\": [\"$scan\"]}" \
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

heading "8. Student downloads the stamped transcript and the request as a Word document"
# document-service learns about the attachment from the STEP_FINISHED event, a moment after step 1.
for _ in $(seq 1 20); do
  link=$(curl -sS -H "Authorization: Bearer $student" "$api/files/$scan/download")
  [ "$(echo "$link" | jq -r '.url // empty')" ] && break
  sleep 0.5
done
curl -sS --fail -o "$work/download.pdf" "$(echo "$link" | jq -r .url)"
echo "   transcript: $(wc -c < "$work/download.pdf") bytes, identical to the upload: $(cmp -s "$work/scan.pdf" "$work/download.pdf" && echo yes || echo no)"
call "$student" -o "$work/request.docx" "$api/requests/$id/document"
echo "   request.docx: $(wc -c < "$work/request.docx") bytes"

heading "9. Academic Affairs downloads the files of the request as a ZIP, built in the background"
selection="{\"requestIds\": [$id]}"
call "$academic" -X POST -d "$selection" "$api/files/bulk-downloads/preview" \
  | jq -r '"   preview: \(.visibleRequests) request(s), \(.files) file(s), \(.totalBytes) bytes"'
job=$(call "$academic" -X POST -d "$selection" "$api/files/bulk-downloads" | jq -r .id)
for _ in $(seq 1 30); do
  status=$(call "$academic" "$api/files/bulk-downloads/$job")
  [ "$(echo "$status" | jq -r .status)" = READY ] && break
  sleep 1
done
curl -sS --fail -o "$work/files.zip" "$(echo "$status" | jq -r .downloadUrl)"
# Listed with Python: some unzip builds misread UTF-8 entry names such as Vietnamese file names.
python3 -c 'import sys, zipfile; print("\n".join("   " + name for name in zipfile.ZipFile(sys.argv[1]).namelist()))' \
  "$work/files.zip"

heading "10. Emails, delivered through Kafka and notification-service (Mailpit inbox: http://localhost:8025)"
code=$(echo "$request" | jq -r .code)
mailpit="${MAILPIT_URL:-http://localhost:8025}/api/v1"
for _ in $(seq 1 30); do
  emails=$(curl -sS -G "$mailpit/search" --data-urlencode "query=subject:\"[$code]\"")
  [ "$(echo "$emails" | jq '.messages_count')" -ge 4 ] && break
  sleep 1
done
echo "$emails" | jq -r '.messages | sort_by(.Created)[] | "   to \(.To[0].Address): \(.Subject)"'
