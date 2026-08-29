#!/data/data/com.termux/files/usr/bin/bash
# 主动模式守护进程: 长轮询服务端命令通道 + crond 看门狗
# 收到"立即定位"命令 → 立刻运行 report.sh 并销单 → 浏览器 8~25 秒内看到新位置
set -uo pipefail

BASEDIR=$(cd "$(dirname "$0")" && pwd)
CONFIG="$BASEDIR/config.env"
[ -f "$CONFIG" ] || { echo "[agent] 缺少 config.env, 请先运行 setup.sh"; exit 1; }
# shellcheck source=/dev/null
source "$CONFIG"
: "${API_BASE:?config.env 缺少 API_BASE}"
: "${DEVICE_KEY:?config.env 缺少 DEVICE_KEY}"
DEVICE_NAME="${DEVICE_NAME:-redmi-note15pro}"
AGENT_INTERVAL="${AGENT_INTERVAL:-50}"

log() { echo "[$(date '+%F %T')] [agent] $*" >> "$BASEDIR/agent.log"; }

if pgrep -f "agent\.sh" | grep -qv "^$$\$"; then
  log "检测到已有 agent 实例, 退出避免重复"
  exit 0
fi

log "守护启动: 长轮询挂 ${AGENT_INTERVAL}s, 设备=$DEVICE_NAME"

while true; do
  # 看门狗: crond 被杀则拉起
  if ! pgrep crond >/dev/null 2>&1; then
    crond 2>/dev/null && log "看门狗: 重启 crond"
  fi

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
      # 网络故障, 稍等重试
      sleep 10
      ;;
    *)
      log "poll 异常 HTTP $code, 休 10s"
      sleep 10
      ;;
  esac
  sleep 1
done
