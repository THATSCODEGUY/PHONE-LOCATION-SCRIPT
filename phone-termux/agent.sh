#!/data/data/com.termux/files/usr/bin/bash
# 主动+被动统一守护进程 v2.2
# · 长轮询命令通道: 收到[获取位置] → 立即 report.sh 销单 → 浏览器 8~25 秒出最新位置
# · 时间制被动上报: 距上次被动 ≥ PASSIVE_MIN 分钟 → report.sh
#   (由活跃循环驱动, 天然不受安卓 Doze 定时器推迟影响; cron 仅做看门狗)
set -uo pipefail

BASEDIR=$(cd "$(dirname "$0")" && pwd)
CONFIG="$BASEDIR/config.env"
STATE="$BASEDIR/last_passive.txt"
[ -f "$CONFIG" ] || { echo "[agent] 缺少 config.env, 请先运行 setup.sh"; exit 1; }
# shellcheck source=/dev/null
source "$CONFIG"
: "${API_BASE:?config.env 缺少 API_BASE}"
: "${DEVICE_KEY:?config.env 缺少 DEVICE_KEY}"
DEVICE_NAME="${DEVICE_NAME:-redmi-note15pro}"
AGENT_INTERVAL="${AGENT_INTERVAL:-50}"
PASSIVE_MIN="${PASSIVE_MIN:-60}"
PASSIVE_SEC=$((PASSIVE_MIN * 60))

log() { echo "[$(date '+%F %T')] [agent] $*" >> "$BASEDIR/agent.log"; }

if pgrep -f "agent\.sh" | grep -qv "^$$\$"; then
  log "检测到已有 agent 实例, 退出避免重复"
  exit 0
fi

log "守护启动: 长轮询 ${AGENT_INTERVAL}s, 被动每 ${PASSIVE_MIN}min, 设备=$DEVICE_NAME"

while true; do
  # --- 时间制被动上报 (不受 Doze 影响) ---
  last_passive=$(cat "$STATE" 2>/dev/null || echo 0)
  now=$(date +%s)
  if [ $((now - last_passive)) -ge "$PASSIVE_SEC" ]; then
    log "被动上报 (距上次 $((now - last_passive))s)"
    if bash "$BASEDIR/report.sh"; then
      date +%s > "$STATE"
      log "被动上报完成"
    else
      log "被动上报失败, 下轮重试"
    fi
  fi

  # --- 长轮询命令通道 ---
  resp=$(curl -sS --get -m $((AGENT_INTERVAL + 15)) -w '\n%{http_code}' \
    -H "X-Device-Key: $DEVICE_KEY" \
    --data-urlencode "device=$DEVICE_NAME" \
    -d "wait=$AGENT_INTERVAL" \
    "$API_BASE/api/poll" 2>/dev/null)
  code=$(printf '%s' "$resp" | tail -n1)
  body=$(printf '%s' "$resp" | sed '$d')

  case "$code" in
    200)
      cmd_id=$(printf '%s' "$body" | jq -r '.id // empty' 2>/dev/null)
      cmd_type=$(printf '%s' "$body" | jq -r '.type // "locate"' 2>/dev/null)
      if [ -n "$cmd_id" ]; then
        log "收到命令 #$cmd_id ($cmd_type), 立即定位上报"
        if bash "$BASEDIR/report.sh" "$cmd_id"; then
          log "命令 #$cmd_id 完成"
        else
          log "命令 #$cmd_id 执行失败 (report.sh 退出非零)"
        fi
      fi
      ;;
    204)
      : # 挂满超时, 无命令, 正常, 立即重连
      ;;
    000)
      sleep 10
      ;;
    *)
      log "poll 异常 HTTP $code, 休 10s"
      sleep 10
      ;;
  esac
  sleep 1
done
