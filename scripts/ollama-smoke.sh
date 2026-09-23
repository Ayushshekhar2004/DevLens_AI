#!/usr/bin/env bash
set -euo pipefail

api_base_url="${DEVLENS_SMOKE_API_URL:-http://localhost:8080}"
profile_id="${DEVLENS_SMOKE_PROFILE:-local}"
model_name="${DEVLENS_SMOKE_MODEL:-}"

if [[ -z "$model_name" ]]; then
  echo "DEVLENS_SMOKE_MODEL must name a model already installed on the selected Ollama profile." >&2
  exit 2
fi

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required for this smoke check." >&2
    exit 2
  fi
done

temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

email="devlens-ollama-smoke-$(date +%s)-$$@example.invalid"
password="Smoke-$(date +%s)-$$-Only"
auth_header="$temporary_directory/auth-header"

request() {
  local method="$1"
  local path="$2"
  local output="$3"
  local data="${4:-}"
  local -a arguments=(--silent --show-error --output "$output" --write-out '%{http_code}'
    --request "$method" "$api_base_url$path")
  if [[ -s "$auth_header" ]]; then
    arguments+=(--header "$(<"$auth_header")")
  fi
  if [[ -n "$data" ]]; then
    arguments+=(--header 'Content-Type: application/json' --data "$data")
  fi
  curl "${arguments[@]}"
}

health_status="$(request GET /api/health "$temporary_directory/health.json")"
[[ "$health_status" == "200" ]] || { echo "DevLens health check failed with HTTP $health_status." >&2; exit 1; }

unauthorized_status="$(request GET '/api/analyses/history?page=0&size=1&sort=newest' "$temporary_directory/unauthorized.json")"
[[ "$unauthorized_status" == "401" ]] || { echo "Unauthenticated history check returned HTTP $unauthorized_status, expected 401." >&2; exit 1; }

register_body="$(jq -cn --arg name 'Ollama Smoke' --arg email "$email" --arg password "$password" \
  '{name:$name,email:$email,password:$password}')"
register_status="$(request POST /api/auth/register "$temporary_directory/register.json" "$register_body")"
[[ "$register_status" == "201" ]] || { echo "Smoke registration failed with HTTP $register_status." >&2; exit 1; }

login_body="$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
login_status="$(request POST /api/auth/login "$temporary_directory/login.json" "$login_body")"
[[ "$login_status" == "200" ]] || { echo "Smoke login failed with HTTP $login_status." >&2; exit 1; }
token="$(jq -er '.token' "$temporary_directory/login.json")"
printf 'Authorization: Bearer %s' "$token" > "$auth_header"

profiles_status="$(request GET /api/ai/ollama/profiles "$temporary_directory/profiles.json")"
[[ "$profiles_status" == "200" ]] || { echo "Profile discovery failed with HTTP $profiles_status." >&2; exit 1; }
jq -e --arg profile "$profile_id" 'any(.[]; .id == $profile)' "$temporary_directory/profiles.json" >/dev/null \
  || { echo "Configured profile is not exposed by the authenticated metadata endpoint." >&2; exit 1; }

connection_status="$(request POST "/api/ai/ollama/profiles/$profile_id/test" "$temporary_directory/models.json")"
[[ "$connection_status" == "200" ]] || { echo "Ollama connection test failed with HTTP $connection_status." >&2; exit 1; }
jq -e --arg model "$model_name" '.models | index($model) != null' "$temporary_directory/models.json" >/dev/null \
  || { echo "Selected model is not installed on the chosen profile." >&2; exit 1; }

source_code='public class Main { public static int add(int a, int b) { return a + b; } }'
analysis_body="$(jq -cn --arg source "$source_code" '{language:"JAVA",sourceCode:$source}')"
started_at="$(date +%s)"
analysis_status="$(request POST "/api/analyses?ollamaProfile=$profile_id&ollamaModel=$model_name" \
  "$temporary_directory/analysis.json" "$analysis_body")"
elapsed_seconds="$(( $(date +%s) - started_at ))"
[[ "$analysis_status" == "201" ]] || { echo "Ollama analysis failed with HTTP $analysis_status." >&2; exit 1; }
analysis_id="$(jq -er 'select(.status == "COMPLETED") | .id' "$temporary_directory/analysis.json")"
jq -e '.result.summary | type == "string" and length > 0' "$temporary_directory/analysis.json" >/dev/null

history_status="$(request GET '/api/analyses/history?page=0&size=20&sort=newest' "$temporary_directory/history.json")"
[[ "$history_status" == "200" ]] || { echo "History verification failed with HTTP $history_status." >&2; exit 1; }
jq -e --argjson id "$analysis_id" 'any(.content[]; .id == $id and .status == "COMPLETED")' \
  "$temporary_directory/history.json" >/dev/null

delete_status="$(request DELETE "/api/analyses/$analysis_id" "$temporary_directory/delete.json")"
[[ "$delete_status" == "204" ]] || { echo "Smoke cleanup failed with HTTP $delete_status." >&2; exit 1; }

echo "Ollama smoke check passed: authenticated analysis and persisted history verified in ${elapsed_seconds}s."
