#!/bin/bash
set -euo pipefail
umask 077

BOOTSTRAP=/opt/keycloak/bootstrap
command=${1:-start}
if [[ $# == 0 ]]; then set -- start; fi
if [[ $command != start && $command != start-dev ]]; then
  exec /opt/keycloak/bin/kc.sh "$@"
fi
if [[ $command == start && ${CEEDS_BOOTSTRAP_DEMO_USER:-false} == true ]]; then
  echo 'CEEDS_BOOTSTRAP_DEMO_USER=true is only allowed with start-dev.' >&2
  exit 1
fi

# Bootstrap connects to local HTTP, including command-line overrides.
args=("$@")
for ((i=1; i<${#args[@]}; i++)); do
  case ${args[$i]} in
    --http-port=*) export KC_HTTP_PORT=${args[$i]#*=} ;;
    --http-port) i=$((i+1)); export KC_HTTP_PORT=${args[$i]} ;;
    --http-relative-path=*) export KC_HTTP_RELATIVE_PATH=${args[$i]#*=} ;;
    --http-relative-path) i=$((i+1)); export KC_HTTP_RELATIVE_PATH=${args[$i]} ;;
    --http-enabled=*) export KC_HTTP_ENABLED=${args[$i]#*=} ;;
    --http-enabled) i=$((i+1)); export KC_HTTP_ENABLED=${args[$i]} ;;
    --http-management-port=*) export KC_HTTP_MANAGEMENT_PORT=${args[$i]#*=} ;;
    --http-management-port) i=$((i+1)); export KC_HTTP_MANAGEMENT_PORT=${args[$i]} ;;
    --http-management-relative-path=*) export KC_HTTP_MANAGEMENT_RELATIVE_PATH=${args[$i]#*=} ;;
    --http-management-relative-path) i=$((i+1)); export KC_HTTP_MANAGEMENT_RELATIVE_PATH=${args[$i]} ;;
  esac
done
if [[ ${KC_HTTP_ENABLED:-true} != true ]]; then
  echo 'CEEDS bootstrap requires KC_HTTP_ENABLED=true (local HTTP endpoint).' >&2
  exit 1
fi
if [[ -z ${KC_BOOTSTRAP_ADMIN_USERNAME:-} || -z ${KC_BOOTSTRAP_ADMIN_PASSWORD:-} ]]; then
  echo 'CEEDS bootstrap requires KC_BOOTSTRAP_ADMIN_USERNAME and KC_BOOTSTRAP_ADMIN_PASSWORD, including on existing realms.' >&2
  exit 1
fi
if [[ $command == start-dev ]]; then
  export CEEDS_DIRECTORY_CLIENT_SECRET=${CEEDS_DIRECTORY_CLIENT_SECRET:-ceeds-directory-dev-secret}
elif [[ -z ${CEEDS_DIRECTORY_CLIENT_SECRET:-} ]]; then
  echo 'CEEDS_DIRECTORY_CLIENT_SECRET is required for production startup.' >&2
  exit 1
fi

work=$(mktemp -d /tmp/ceeds-bootstrap.XXXXXX)
server_pid=
bootstrap_pid=
# shellcheck disable=SC2317
# Invoked indirectly by the EXIT trap, including interrupted wait calls.
cleanup() {
  trap - TERM INT
  if [[ -n $bootstrap_pid ]]; then kill -TERM -- "-$bootstrap_pid" 2>/dev/null || true; fi
  if [[ -n $server_pid ]]; then kill -TERM "$server_pid" 2>/dev/null || true; fi
  if [[ -n $bootstrap_pid ]]; then wait "$bootstrap_pid" 2>/dev/null || true; fi
  if [[ -n $server_pid ]]; then wait "$server_pid" 2>/dev/null || true; fi
  rm -rf "$work"
  rm -f /tmp/ceeds-bootstrap-ready /tmp/ceeds-health-config.json /opt/keycloak/data/import/ceeds-realm.json
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
rm -f /tmp/ceeds-bootstrap-ready
bash "$BOOTSTRAP/bootstrap-config.sh" > "$work/config.json"
# Docker/Kubernetes exec probes do not inherit entrypoint's exported CLI overrides.
jq -n --arg port "${KC_HTTP_MANAGEMENT_PORT:-9000}" \
  --arg path "${KC_HTTP_MANAGEMENT_RELATIVE_PATH:-${KC_HTTP_RELATIVE_PATH:-/}}" \
  '{port: $port, path: $path}' > /tmp/ceeds-health-config.json

# JSON generation escapes secrets and URLs; no substitution into JSON text.
mkdir -p /opt/keycloak/data/import
jq --slurpfile config "$work/config.json" --arg secret "$CEEDS_DIRECTORY_CLIENT_SECRET" '
  .clients |= map(
    if .clientId == "ceeds-frontend" then
      .rootUrl = $config[0].frontendUrls[0] |
      .redirectUris = ($config[0].frontendUrls | map(. + "/*")) |
      .webOrigins = ($config[0].frontendUrls | map(capture("^(?<origin>https?://[^/]+)").origin)) |
      .attributes["post.logout.redirect.uris"] = ($config[0].frontendUrls | map(. + "/*") | join("##"))
    elif .clientId == "ceeds-directory" then .secret = $secret else . end) |
  .users |= map(select(.username != "ceeds" or $config[0].demoUser))
  ' "$BOOTSTRAP/ceeds-realm.json" > /opt/keycloak/data/import/ceeds-realm.json

/opt/keycloak/bin/kc.sh "$@" --import-realm &
server_pid=$!
# Covers authentication and all admin calls, including hung requests.
timeout --signal=TERM --kill-after=5s 180s bash "$BOOTSTRAP/org-bootstrap.sh" "$work" &
bootstrap_pid=$!
while kill -0 "$bootstrap_pid" 2>/dev/null; do
  if ! kill -0 "$server_pid" 2>/dev/null; then
    status=0
    wait "$server_pid" || status=$?
    exit "$((status == 0 ? 1 : status))"
  fi
  sleep 1 & wait $! || true
done
status=0
wait "$bootstrap_pid" || status=$?
bootstrap_pid=
if [[ $status != 0 ]]; then
  echo "CEEDS bootstrap failed (exit $status; startup limit 180s). Check configuration and existing admin credentials." >&2
  exit "$status"
fi
rm -f /opt/keycloak/data/import/ceeds-realm.json
touch /tmp/ceeds-bootstrap-ready
echo 'CEEDS bootstrap complete; readiness enabled.'
status=0
wait "$server_pid" || status=$?
exit "$status"
