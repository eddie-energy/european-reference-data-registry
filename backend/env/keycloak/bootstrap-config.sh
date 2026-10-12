#!/bin/bash
set -euo pipefail

# Keep empty positions: Bash's read/IFS drops trailing empty fields.
defaults=false
if [[ ! ${CEEDS_BOOTSTRAP_ORGANIZATIONS+x} && ! ${CEEDS_BOOTSTRAP_ORGANIZATION_ROLES+x} && ! ${CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS+x} ]]; then
  defaults=true
fi

jq -n \
  --argjson defaults "$defaults" \
  --arg organizations "${CEEDS_BOOTSTRAP_ORGANIZATIONS-}" \
  --arg roles "${CEEDS_BOOTSTRAP_ORGANIZATION_ROLES-}" \
  --arg nations "${CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS-}" \
  --argjson rolesProvided "$(if [[ ${CEEDS_BOOTSTRAP_ORGANIZATION_ROLES+x} ]]; then echo true; else echo false; fi)" \
  --argjson nationsProvided "$(if [[ ${CEEDS_BOOTSTRAP_ORGANIZATION_NATIONS+x} ]]; then echo true; else echo false; fi)" \
  --arg urls "${CEEDS_FRONTEND_URLS-http://localhost:5173;http://localhost:8080}" \
  --arg demo "${CEEDS_BOOTSTRAP_DEMO_USER-false}" '
  def trim: gsub("^\\s+|\\s+$"; "");
  def values: if trim == "" then [""] else split(",") | map(trim | ascii_upcase) | unique end;
  def check($condition; $message): if $condition then . else error($message) end;
  (if $defaults then ["fhooe"] elif $organizations == "" then [] else $organizations | split(";") | map(trim) end) as $aliases |
  (if $defaults then ["OPERATIONAL_ENTITY"] elif $rolesProvided then $roles | split(";") else $aliases | map("") end) as $roleList |
  (if $defaults then [""] elif $nationsProvided then $nations | split(";") else $aliases | map("") end) as $nationList |
  check(($roleList | length) == ($aliases | length); "Organization roles list length must match organizations") |
  check(($nationList | length) == ($aliases | length); "Organization nations list length must match organizations") |
  check(($aliases | unique | length) == ($aliases | length); "Duplicate organization alias") |
  check(all($aliases[]; test("^[A-Za-z0-9][A-Za-z0-9._-]*$")); "Organization aliases must be nonempty and use letters, digits, dot, underscore or hyphen") |
  check($demo == "true" or $demo == "false"; "CEEDS_BOOTSTRAP_DEMO_USER must be true or false") |
  ($urls | split(";") | map(trim | sub("/+$"; ""))) as $frontendUrls |
  check(all($frontendUrls[]; test("^https?://[^/\\s?#]+(/[^\\s?#]*)?$")); "CEEDS_FRONTEND_URLS must contain nonempty HTTP(S) base URLs") |
  [range(0; $aliases | length) as $i |
    ($roleList[$i] | values) as $r | ($nationList[$i] | values) as $n |
    check(all($r[]; . == "" or . == "OPERATIONAL_ENTITY" or . == "NDSF"); "Unsupported organization role") |
    check(all($n[]; . == "" or . == "AUT" or . == "FRA" or . == "ESP" or . == "GER"); "Unsupported organization nation") |
    check(($r | index("NDSF")) == null or ($n | index("")) == null; "NDSF requires at least one nation") |
    {alias: $aliases[$i], name: (if $defaults then "FHOOE" else $aliases[$i] end), enabled: true,
     domains: (if $defaults then [{name: "fh-ooe.at", verified: false}] else [] end),
     attributes: {ceeds_role: $r, ceeds_nations: $n}}] as $orgs |
  {organizations: $orgs, frontendUrls: $frontendUrls, demoUser: ($demo == "true")}
  '
