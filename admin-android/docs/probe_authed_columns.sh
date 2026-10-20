#!/usr/bin/env bash
#
# probe_authed_columns.sh — T02 authenticated column enumeration probe.
#
# PURPOSE
#   The RLS-hidden tables return zero rows to the publishable (anon) key, so their
#   column lists cannot be observed anonymously. This probe signs in as a real
#   ADMIN account and enumerates those columns so they can be pasted into
#   docs/CONTRACT.md (§3.8) and docs/SCHEMA_SNAPSHOT.md.
#
#   Status until this is run with real credentials:
#     BLOCKED (no admin creds — R1)
#
# USAGE
#   cd "C:/Users/sarfaraz/Desktop/vedant fruits and vegitables/admin-android"
#   ADMIN_EMAIL='owner@example.com' ADMIN_PASSWORD='••••••' \
#     bash docs/probe_authed_columns.sh
#
# OPTIONAL ENV OVERRIDES
#   SUPABASE_URL              default: https://hyibaljxicojfoxbihdn.supabase.co
#   SUPABASE_PUBLISHABLE_KEY  default: the repo's publishable key (public by design)
#
# SECURITY
#   - READ-ONLY. This script performs no INSERT/UPDATE/DELETE and no DDL.
#   - It never prints the password or the access token.
#   - It never uses (and must never be given) a service-role key.
#   - The password is sent in a request BODY (not argv) so it does not appear in
#     the process table.
#
# NOTE ON EMPTY TABLES
#   PostgREST cannot reveal a table's columns from zero rows (`[]`). If a table is
#   empty even for the admin, the probe prints "0 rows — columns not observable
#   via row" and saves the PostgREST OpenAPI document to
#   docs/.openapi_authed.json for manual inspection as a fallback.
#
set -euo pipefail

URL="${SUPABASE_URL:-https://hyibaljxicojfoxbihdn.supabase.co}"
KEY="${SUPABASE_PUBLISHABLE_KEY:-sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH}"

if [[ -z "${ADMIN_EMAIL:-}" ]]; then
  echo "ERROR: set ADMIN_EMAIL (a real admin account email)." >&2
  exit 2
fi
if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: set ADMIN_PASSWORD (that account's password)." >&2
  exit 2
fi

# RLS-hidden tables whose columns we need (from CONTRACT.md §2.2 / §10).
TABLES=(
  orders order_items order_status_history profiles addresses cart_items
  complaints complaint_messages complaint_attachments notifications
  notification_outbox events
)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- tiny JSON helpers: prefer python3, then python, then jq, then sed --------

json_field() { # $1=json  $2=top-level key -> value
  local json="$1" key="$2"
  if command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json" | python3 -c \
      'import sys,json;d=json.load(sys.stdin);print(d.get(sys.argv[1],""))' "$key"
  elif command -v python >/dev/null 2>&1; then
    printf '%s' "$json" | python -c \
      'import sys,json;d=json.load(sys.stdin);print(d.get(sys.argv[1],""))' "$key"
  elif command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r --arg k "$key" '.[$k] // ""'
  else
    printf '%s' "$json" | sed -n 's/.*"'"$key"'":"\([^"]*\)".*/\1/p'
  fi
}

json_row_keys_sorted() { # $1=json array -> newline-separated sorted keys of row[0]
  local json="$1"
  if command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json" | python3 -c \
      'import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    d=None
print("\n".join(sorted(d[0].keys())) if isinstance(d,list) and d and isinstance(d[0],dict) else "")'
  elif command -v python >/dev/null 2>&1; then
    printf '%s' "$json" | python -c \
      'import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    d=None
print("\n".join(sorted(d[0].keys())) if isinstance(d,list) and d and isinstance(d[0],dict) else "")'
  elif command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r 'if (type=="array" and length>0) then (.[0]|keys_unsorted|sort|join("\n")) else "" end'
  else
    echo ""
  fi
}

json_row_count() { # $1=json array -> element count
  local json="$1"
  if command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json" | python3 -c \
      'import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    d=None
print(len(d) if isinstance(d,list) else 0)'
  elif command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r 'if type=="array" then length else 0 end'
  else
    echo "0"
  fi
}

echo "== Vedant Admin · T02 authenticated column probe =="
echo "   project : $URL"
echo "   account : $ADMIN_EMAIL"
echo

# --- 1. sign in ---------------------------------------------------------------
# Password goes in the request body (via --data-binary on a variable), never argv.
LOGIN_BODY="$(printf '{"email":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
LOGIN_RESP="$(
  curl -s -X POST "$URL/auth/v1/token?grant_type=password" \
    -H "apikey: $KEY" \
    -H "Content-Type: application/json" \
    --data-binary "$LOGIN_BODY"
)"
unset LOGIN_BODY

TOKEN="$(json_field "$LOGIN_RESP" access_token)"
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: sign-in failed — no access_token returned." >&2
  # Print the server's error message only (contains no password).
  echo "server said: $(json_field "$LOGIN_RESP" error_description)$(json_field "$LOGIN_RESP" msg)" >&2
  exit 1
fi
echo "sign-in: OK (access token acquired; not shown)"
echo

# --- 2. enumerate columns per table ------------------------------------------
ANY_EMPTY=0
for t in "${TABLES[@]}"; do
  resp="$(
    curl -s "$URL/rest/v1/$t?select=*&limit=1" \
      -H "apikey: $KEY" \
      -H "Authorization: Bearer $TOKEN"
  )"
  n="$(json_row_count "$resp")"
  echo "### $t"
  if [[ "$n" -ge 1 ]]; then
    echo "anon-visible: NO (RLS) · rows sampled: $n · columns (sorted):"
    json_row_keys_sorted "$resp" | sed 's/^/    /'
  else
    echo "anon-visible: NO (RLS) · rows sampled: 0 — columns not observable via row"
    echo "    (table is empty even for the admin; use the OpenAPI fallback below)"
    ANY_EMPTY=1
  fi
  echo
done

# --- 3. OpenAPI fallback (only if some table was empty) -----------------------
if [[ "$ANY_EMPTY" -eq 1 ]]; then
  OUT="$SCRIPT_DIR/.openapi_authed.json"
  echo "== OpenAPI fallback =="
  echo "Fetching $URL/rest/v1/ with the admin token -> $OUT"
  curl -s "$URL/rest/v1/" \
    -H "apikey: $KEY" \
    -H "Authorization: Bearer $TOKEN" \
    -o "$OUT" || true
  echo "Saved. If it contains {\"swagger\":...}, table columns are under"
  echo "  .definitions.<table>.properties  (keys = column names)."
  echo "If it contains {\"message\":\"Secret API key required\"}, the spec is blocked;"
  echo "insert one throwaway row into the empty table and re-run instead."
  echo
fi

echo "Done. Paste the column blocks into docs/CONTRACT.md §3.8 and"
echo "docs/SCHEMA_SNAPSHOT.md, then flip those tables' status from"
echo "'BLOCKED (no admin creds — R1)' to 'enumerated'."
