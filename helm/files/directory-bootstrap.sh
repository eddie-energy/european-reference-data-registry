#!/bin/bash
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER=${KEYCLOAK_SERVER:?}
REALM=${KEYCLOAK_REALM:-ceeds}
CLIENT_ID=ceeds-directory
FRONTEND_CLIENT_ID=ceeds-frontend
CLIENT_SECRET=${KEYCLOAK_DIRECTORY_CLIENT_SECRET:?}

json_field() {
  grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*"\([^"]*\)"$/\1/'
}

for attempt in {1..30}; do
  if $KCADM config credentials --server "$SERVER" --realm master \
      --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
$KCADM get "realms/$REALM" >/dev/null
$KCADM update "realms/$REALM" -s organizationsEnabled=true

frontend_client_uuid=$($KCADM get clients -r "$REALM" -q "clientId=$FRONTEND_CLIENT_ID" --fields id | json_field id)
scope_uuid=$($KCADM get client-scopes -r "$REALM" --fields id,name \
  | tr -d '\n ' | sed 's/},{/}\n{/g' | grep '"name":"organization"' | json_field id)
$KCADM delete "clients/$frontend_client_uuid/optional-client-scopes/$scope_uuid" -r "$REALM" 2>/dev/null || true
$KCADM update "clients/$frontend_client_uuid/default-client-scopes/$scope_uuid" -r "$REALM"

mapper_uuid=$($KCADM get "client-scopes/$scope_uuid/protocol-mappers/models" -r "$REALM" --fields id,protocolMapper \
  | tr -d '\n ' | sed 's/},{/}\n{/g' | grep '"protocolMapper":"oidc-organization-membership-mapper"' | json_field id)
$KCADM update "client-scopes/$scope_uuid/protocol-mappers/models/$mapper_uuid" -r "$REALM" \
  -s 'config."addOrganizationId"=true' \
  -s 'config."addOrganizationAttributes"=true'

client_uuid=$($KCADM get clients -r "$REALM" -q "clientId=$CLIENT_ID" --fields id | json_field id || true)
if [ -z "$client_uuid" ]; then
  $KCADM create clients -r "$REALM" \
    -s "clientId=$CLIENT_ID" -s enabled=true -s publicClient=false \
    -s serviceAccountsEnabled=true -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false -s "secret=$CLIENT_SECRET" >/dev/null
  client_uuid=$($KCADM get clients -r "$REALM" -q "clientId=$CLIENT_ID" --fields id | json_field id)
else
  $KCADM update "clients/$client_uuid" -r "$REALM" \
    -s enabled=true -s publicClient=false -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false -s directAccessGrantsEnabled=false \
    -s "secret=$CLIENT_SECRET" >/dev/null
fi

service_user_id=$($KCADM get "clients/$client_uuid/service-account-user" -r "$REALM" | json_field id)
for role in view-organizations manage-organizations view-users manage-users; do
  $KCADM add-roles -r "$REALM" --uid "$service_user_id" \
    --cclientid realm-management --rolename "$role"
done
