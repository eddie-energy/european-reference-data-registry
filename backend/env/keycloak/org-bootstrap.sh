#!/bin/bash
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER=${KEYCLOAK_SERVER:-http://keycloak:8080}
REALM=${KEYCLOAK_REALM:-ceeds}
ADMIN_USER=${KEYCLOAK_ADMIN:-admin}
ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}

ORGANIZATIONS=(
  "fhooe|FHOOE|fh-ooe.at|OPERATIONAL_ENTITY|"
)

MEMBERSHIPS=(
  "ceeds|fhooe"
)

json_field() {
  grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*"\([^"]*\)"$/\1/'
}

as_json_array() {
  if [ -z "$1" ]; then
    printf '[""]'
  else
    printf '["%s"]' "$(echo "$1" | sed 's/,/","/g')"
  fi
}

echo "Waiting for $SERVER ..."
until $KCADM config credentials --server "$SERVER" --realm master \
    --user "$ADMIN_USER" --password "$ADMIN_PASSWORD" >/dev/null 2>&1; do
  sleep 2
done

echo "Enabling organizations on realm $REALM"
$KCADM update "realms/$REALM" -s organizationsEnabled=true

echo "Enabling organization default attributes listener on realm $REALM"
$KCADM update events/config -r "$REALM" \
  -s 'eventsListeners=["jboss-logging","org-default-attrs"]'

echo "Reducing the registration form to username and password"
$KCADM update "users/profile" -r "$REALM" -f - <<'PROFILE'
{
  "attributes": [
    {
      "name": "username",
      "displayName": "${username}",
      "validations": {
        "length": { "min": 3, "max": 255 },
        "username-prohibited-characters": {},
        "up-username-not-idn-homograph": {}
      },
      "permissions": { "view": [ "admin", "user" ], "edit": [ "admin", "user" ] },
      "multivalued": false,
      "required": { "roles": [ "user" ] }
    },
    {
      "name": "email",
      "displayName": "${email}",
      "validations": { "email": {}, "length": { "max": 255 } },
      "permissions": { "view": [ "admin" ], "edit": [ "admin" ] },
      "multivalued": false
    },
    {
      "name": "ceeds_country",
      "displayName": "Country",
      "validations": { "options": { "options": [ "AUT", "FRA", "ESP", "GER" ] } },
      "permissions": { "view": [ "admin" ], "edit": [ "admin" ] },
      "multivalued": false
    }
  ],
  "groups": []
}
PROFILE

for entry in "${ORGANIZATIONS[@]}"; do
  IFS='|' read -r alias name domain roles nations <<< "$entry"
  org_uuid=$($KCADM get organizations -r "$REALM" --fields id,alias \
    | tr -d '\n ' | sed 's/},{/}\n{/g' | grep "\"alias\":\"$alias\"" | json_field id || true)

  if [ -z "$org_uuid" ]; then
    echo "Creating organization $alias"
    $KCADM create organizations -r "$REALM" \
      -s "alias=$alias" -s "name=$name" -s enabled=true \
      -s "domains=[{\"name\":\"$domain\",\"verified\":false}]" \
      -s "attributes.ceeds_role=$(as_json_array "$roles")" \
      -s "attributes.ceeds_nations=$(as_json_array "$nations")"
    org_uuid=$($KCADM get organizations -r "$REALM" --fields id,alias \
      | tr -d '\n ' | sed 's/},{/}\n{/g' | grep "\"alias\":\"$alias\"" | json_field id)
  else
    echo "Updating organization $alias"
    $KCADM update "organizations/$org_uuid" -r "$REALM" \
      -s "attributes.ceeds_role=$(as_json_array "$roles")" \
      -s "attributes.ceeds_nations=$(as_json_array "$nations")"
  fi
  eval "ORG_UUID_${alias}=$org_uuid"
done

echo "Backfilling organization default attributes"
$KCADM get organizations -r "$REALM" --fields id \
  | tr -d '\n ' | sed 's/},{/}\n/g' \
  | while IFS= read -r organization_json; do
    org_uuid=$(printf '%s' "$organization_json" | json_field id)
    if [ -n "$org_uuid" ]; then
      $KCADM get "organizations/$org_uuid" -r "$REALM" \
        | $KCADM update "organizations/$org_uuid" -r "$REALM" -f -
    fi
  done

for entry in "${MEMBERSHIPS[@]}"; do
  IFS='|' read -r username alias <<< "$entry"
  eval "org_uuid=\$ORG_UUID_${alias}"
  user_uuid=$($KCADM get users -r "$REALM" -q "username=$username" --fields id | json_field id)
  if [ -z "$user_uuid" ]; then
    echo "User $username not found, skipping membership in $alias"
    continue
  fi
  if $KCADM get "organizations/$org_uuid/members" -r "$REALM" --fields username | grep -q "\"$username\""; then
    echo "User $username already a member of $alias"
  else
    echo "Adding $username to $alias"
    $KCADM create "organizations/$org_uuid/members" -r "$REALM" -f - <<< "\"$user_uuid\""
  fi
done

/bin/bash /opt/keycloak/bootstrap/directory-bootstrap.sh
echo "Keycloak organization bootstrap done."
