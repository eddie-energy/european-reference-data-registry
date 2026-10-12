#!/bin/bash
set -euo pipefail
umask 077

BOOTSTRAP=/opt/keycloak/bootstrap
work=$1
config=$work/config.json
KCADM=/opt/keycloak/bin/kcadm.sh
REALM=ceeds
path=${KC_HTTP_RELATIVE_PATH:-/}
SERVER="http://127.0.0.1:${KC_HTTP_PORT:-8080}/${path#/}"
SERVER=${SERVER%/}

admin() { "$KCADM" "$@" --config "$work/kcadm.config"; }
get() { admin get "$1" -r "$REALM" "${@:2}"; }
put() { admin update "$1" -r "$REALM" -f -; }
post() { admin create "$1" -r "$REALM" -f - "${@:2}"; }
required_id() { jq -er '.[0].id // error("Required Keycloak resource missing")'; }

echo 'Waiting for local Keycloak admin endpoint ...'
until admin config credentials --server "$SERVER" --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1; do
  sleep 2
done

admin update "realms/$REALM" -s organizationsEnabled=true
get events/config | jq '.eventsListeners = ((.eventsListeners // []) + ["jboss-logging", "org-default-attrs"] | unique)' | put events/config
put users/profile < "$BOOTSTRAP/user-profile.json"

# Import is skipped for existing realms. Reconcile required clients explicitly.
jq '.clients[] | select(.clientId == "ceeds-frontend")' "$BOOTSTRAP/ceeds-realm.json" > "$work/frontend.json"
jq --slurpfile config "$config" '
  .rootUrl = $config[0].frontendUrls[0] |
  .redirectUris = ($config[0].frontendUrls | map(. + "/*")) |
  .webOrigins = ($config[0].frontendUrls | map(capture("^(?<origin>https?://[^/]+)").origin)) |
  .attributes["post.logout.redirect.uris"] = ($config[0].frontendUrls | map(. + "/*") | join("##"))
  ' "$work/frontend.json" > "$work/frontend-configured.json"
clients=$(get clients -q clientId=ceeds-frontend)
if [[ $(jq 'length' <<< "$clients") == 0 ]]; then
  client_uuid=$(post clients -i < "$work/frontend-configured.json")
else
  client_uuid=$(required_id <<< "$clients")
  get "clients/$client_uuid" | jq --slurpfile desired "$work/frontend-configured.json" '
    .rootUrl = $desired[0].rootUrl | .redirectUris = $desired[0].redirectUris |
    .webOrigins = $desired[0].webOrigins | .attributes = ((.attributes // {}) + $desired[0].attributes)
    ' | put "clients/$client_uuid"
fi
scope_uuid=$(get client-scopes | jq -er '[.[] | select(.name == "organization")][0].id // error("Organization scope missing")')
if get "clients/$client_uuid/optional-client-scopes" | jq -e --arg id "$scope_uuid" 'any(.[]; .id == $id)' >/dev/null; then
  admin delete "clients/$client_uuid/optional-client-scopes/$scope_uuid" -r "$REALM"
fi
admin update "clients/$client_uuid/default-client-scopes/$scope_uuid" -r "$REALM"
mapper=$(get "client-scopes/$scope_uuid/protocol-mappers/models" | jq -ec '
  [.[] | select(.protocolMapper == "oidc-organization-membership-mapper")][0] // error("Organization mapper missing")')
mapper_uuid=$(jq -r '.id' <<< "$mapper")
jq '.config.addOrganizationId = "true" | .config.addOrganizationAttributes = "true"' <<< "$mapper" | put "client-scopes/$scope_uuid/protocol-mappers/models/$mapper_uuid"

clients=$(get clients -q clientId=ceeds-directory)
jq --arg secret "$CEEDS_DIRECTORY_CLIENT_SECRET" '.clients[] | select(.clientId == "ceeds-directory") | .secret = $secret' "$BOOTSTRAP/ceeds-realm.json" > "$work/directory.json"
if [[ $(jq 'length' <<< "$clients") == 0 ]]; then
  directory_uuid=$(post clients -i < "$work/directory.json")
else
  directory_uuid=$(required_id <<< "$clients")
  get "clients/$directory_uuid" | jq --slurpfile desired "$work/directory.json" '. + $desired[0]' | put "clients/$directory_uuid"
fi
service_user=$(get "clients/$directory_uuid/service-account-user" | jq -er .id)
management_uuid=$(get clients -q clientId=realm-management | required_id)
get "clients/$management_uuid/roles/view-organizations" | jq '[.]' | post "users/$service_user/role-mappings/clients/$management_uuid"

# Organization endpoint defaults to a limited page. Always traverse all pages.
organizations='[]'
first=0
while true; do
  page=$(get organizations -q "first=$first" -q max=100 -q briefRepresentation=false)
  organizations=$(jq -n --argjson all "$organizations" --argjson page "$page" '$all + $page')
  [[ $(jq 'length' <<< "$page") == 100 ]] || break
  first=$((first + 100))
done
jq -c '.organizations[]' "$config" > "$work/organizations.jsonl"
while IFS= read -r desired; do
  alias=$(jq -r .alias <<< "$desired")
  org_uuid=$(jq -r --arg alias "$alias" '[.[] | select(.alias == $alias)][0].id // empty' <<< "$organizations")
  if [[ -z $org_uuid ]]; then
    echo "Creating organization $alias"
    org_uuid=$(post organizations -i <<< "$desired")
    organizations=$(jq --arg id "$org_uuid" --argjson org "$desired" '. + [$org + {id: $id}]' <<< "$organizations")
  else
    echo "Updating organization $alias"
    get "organizations/$org_uuid" | jq --argjson desired "$desired" '.attributes = ((.attributes // {}) + $desired.attributes)' | put "organizations/$org_uuid"
    organizations=$(jq --arg id "$org_uuid" --argjson desired "$desired" '
      map(if .id == $id then .attributes = ((.attributes // {}) + $desired.attributes) else . end)
      ' <<< "$organizations")
  fi
done < "$work/organizations.jsonl"
jq -c '.[] | select((.attributes.ceeds_role // [] | length) == 0 or
                    (.attributes.ceeds_nations // [] | length) == 0)' <<< "$organizations" > "$work/backfill.jsonl"
while IFS= read -r organization; do
  org_uuid=$(jq -r .id <<< "$organization")
  jq '
    .attributes //= {} |
    if (.attributes.ceeds_role // [] | length) == 0 then .attributes.ceeds_role = [""] else . end |
    if (.attributes.ceeds_nations // [] | length) == 0 then .attributes.ceeds_nations = [""] else . end
    ' <<< "$organization" | put "organizations/$org_uuid"
done < "$work/backfill.jsonl"

if [[ $(jq -r .demoUser "$config") == true ]]; then
  users=$(get users -q username=ceeds -q exact=true)
  if [[ $(jq 'length' <<< "$users") == 0 ]]; then
    user_uuid=$(jq '.users[] | select(.username == "ceeds")' "$BOOTSTRAP/ceeds-realm.json" | post users -i)
  else
    user_uuid=$(required_id <<< "$users")
  fi
  org_uuid=$(jq -r '[.[] | select(.alias == "fhooe")][0].id // empty' <<< "$organizations")
  if [[ -n $org_uuid ]]; then
    # User memberships avoid paging through all organization members.
    if ! get "organizations/members/$user_uuid/organizations" | jq -e --arg id "$org_uuid" 'any(.[]; .id == $id)' >/dev/null; then
      jq -n --arg id "$user_uuid" '$id' | post "organizations/$org_uuid/members"
    fi
  fi
fi
echo 'Keycloak organization bootstrap done.'
