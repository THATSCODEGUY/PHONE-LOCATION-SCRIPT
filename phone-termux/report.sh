#!/data/data/com.termux/files/usr/bin/bash
# 定位上报脚本 · Redmi Note 15 Pro (Termux)
# 流程: GPS定位(15s) → 失败降级网络定位 → 读电量 → POST上报 → 失败落盘outbox → 下轮补传
# 参数: $1 = 可选命令ID(主动模式), 上报成功后服务端自动销单
set -uo pipefail

CMD_ID="${1:-}"

BASEDIR=$(cd "$(dirname "$0")" && pwd)
CONFIG="$BASEDIR/config.env"
OUTBOX="$BASEDIR/outbox.jsonl"
[ -f "$CONFIG" ] || { echo "[$(date '+%F %T')] 缺少 config.env, 请先运行 setup.sh"; exit 1; }
# shellcheck source=/dev/null
source "$CONFIG"
: "${API_BASE:?config.env 缺少 API_BASE}"
: "${DEVICE_KEY:?config.env 缺少 DEVICE_KEY}"
DEVICE_NAME="${DEVICE_NAME:-redmi-note15pro}"

log() { echo "[$(date '+%F %T')] $*"; }

send_json() {
  local body="$1" code
  code=$(curl -sS -m 30 -w '%{http_code}' -o /dev/null \
    -X POST "$API_BASE/api/report" \
    -H "Content-Type: application/json" \
    -H "X-Device-Key: $DEVICE_KEY" \
    -d "$body" 2>/dev/null) || return 1
  [ "$code" = "200" ] || [ "$code" = "204" ]
}

flush_outbox() {
  [ -s "$OUTBOX" ] || return 0
  local remaining="" line
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    if send_json "$line"; then
      log "补传成功: ${line:0:80}..."
    else
      remaining+="$line"$'\n'
    fi
  done < "$OUTBOX"
  printf '%s' "$remaining" > "$OUTBOX"
  [ ! -s "$OUTBOX" ]
}

valid_loc() {
  printf '%s' "$1" | jq -e '(.latitude|type)=="number" and (.longitude|type)=="number"' >/dev/null 2>&1
}

loc=$(termux-location -p gps -t 15 2>/dev/null)
prov="gps"
if ! valid_loc "$loc"; then
  log "GPS 定位失败, 降级网络定位"
  loc=$(termux-location -p network -t 10 2>/dev/null)
  prov="network"
fi
if ! valid_loc "$loc"; then
  log "定位彻底失败 (检查: 位置开关/定位权限/Termux:API)"
  flush_outbox || true
  exit 2
fi

batt=$(termux-battery-status 2>/dev/null | jq -r '.percentage // empty')

payload=$(jq -nc --slurpfile l <(printf '%s' "$loc") \
  --arg provider "$prov" --arg device "$DEVICE_NAME" --arg batt "$batt" \
  --arg cmd "$CMD_ID" \
  --argjson ts "$(date -u +%s)" '
  ($l[0]) as $o | {
    lat: $o.latitude,
    lng: $o.longitude,
    accuracy: ($o.accuracy // null),
    speed: ($o.speed // null),
    bearing: ($o.bearing // null),
    provider: $provider,
    device: $device,
    battery: (if $batt == "" then null else ($batt | tonumber) end),
    cmd_id: (if $cmd == "" then null else ($cmd | tonumber) end),
    ts: ($ts | todate)
  }')

[ -n "$payload" ] || { log "payload 构造失败"; exit 3; }

flush_outbox || log "outbox 补传仍有残留, 下轮重试"

if send_json "$payload"; then
  log "上报成功 [$prov] $(printf '%s' "$payload" | jq -r '"\(.lat),\(.lng) ±\(.accuracy // "?")m 电\(.battery // "?")%"')"
else
  printf '%s\n' "$payload" >> "$OUTBOX"
  log "上报失败, 已存入 outbox 待补传 ($(wc -l < "$OUTBOX") 条)"
fi
