#!/bin/bash
set -euo pipefail
[[ -f /tmp/ceeds-bootstrap-ready ]]
path=$(jq -er .path /tmp/ceeds-health-config.json)
port=$(jq -er .port /tmp/ceeds-health-config.json)
path="/${path#/}"
path="${path%/}/health/ready"
exec 3<>/dev/tcp/127.0.0.1/"$port"
printf 'GET %s HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' "$path" >&3
response=$(cat <&3)
[[ $response == *'200 OK'* && $response == *'"status": "UP"'* ]]
