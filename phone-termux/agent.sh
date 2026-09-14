#!/data/data/com.termux/files/usr/bin/bash
# 主动+被动统一守护进程 v2.4 · 免费额度省费版
# · 短轮询+间隙: 挂线 POLL_WAIT(≤5s) → 客户端歇 POLL_GAP(25s) → 占空比 ~17% (原 50s 死挂=100%)
# · 工作窗口: 仅 WORK_DAYS(默认周一~周五) 的 WORK_START~WORK_END 时段运行,
#   窗口外进程不退出、每 10 分钟醒来看表, 到点自动恢复 (watchdog/boot 自启无需改动)
# · 时间制被动上报: 由活跃循环驱动, 天然不受安卓 Doze 定时器推迟影响; 窗口外同样休眠
# · 代价: 主动定位到点慢 POLL_GAP 秒; 夜间/周末地图显示"失联"属预期
set -uo pipefail

BASEDIR=$(cd "$(dirname "$0")" && pwd)
CONFIG="$BASEDIR/config.env"
STATE="$BASEDIR/last_passive.txt"
[ -f "$CONFIG" ] || { echo "[agent] 缺少 config.env, 请先运行 setup.sh"; exit 1; }
# shellcheck source=/dev/null
source "$CONFIG"
: "${API_BASE:?config.env 缺少 API_BASE}"
: "${DEVICE_KEY:?config.env 缺少 DEVICE_KEY}"
DEVICE_NAME="${DEVICE_NAME:-primary}"
POLL_WAIT="${POLL_WAIT:-5}"
POLL_GAP="${POLL_GAP:-25}"
WORK_START="${WORK_START:-0800}"
WORK_END="${WORK_END:-1800}"
WORK_DAYS="${WORK_DAYS:-1-5}"
PASSIVE_MIN="${PASSIVE_MIN:-60}"
PASSIVE_SEC=$((PASSIVE_MIN * 60))

# 兼容提示: 旧配置项已废弃 (服务端现已硬限挂线 ≤5s)
if [ -n "${AGENT_INTERVAL:-}" ]; then
  echo "[$(date '+%F %T')] [agent] 旧配置 AGENT_INTERVAL=${AGENT_INTERVAL} 已废弃, 改用 POLL_WAIT=${POLL_WAIT} + POLL_GAP=${POLL_GAP}" >> "$BASEDIR/agent.log"
fi

log() { echo "[$(date '+%F %T')] [agent] $*" >> "$BASEDIR/agent.log"; }

if pgrep -f "agent\.sh" | grep -qv "^$$\$"; then
  log "检测到已有 agent 实例, 退出避免重复"
  exit 0
fi

# 工作日判断: 支持 "1-5" / "1,3,5" / "1-3,5,7" (1=周一 ... 7=周日)
day_in_set() {
  local d="$1" spec="$2" part a b
  IFS=',' read -ra parts <<< "$spec"
  for part in "${parts[@]}"; do
    if [[ "$part" == *-* ]]; then
      a="${part%-*}"; b="${part#*-}"
      [ "$d" -ge "$a" ] && [ "$d" -le "$b" ] && return 0
    else
      [ "$d" = "$part" ] && return 0
    fi
  done
  return 1
}

# 工作窗口判断: 工作日 且 WORK_START ≤ 当前 HHMM < WORK_END
in_window() {
  day_in_set "$(date +%u)" "$WORK_DAYS" || return 1
  local now
  now=$(date +%H%M)
  [ "$now" -ge "$WORK_START" ] && [ "$now" -lt "$WORK_END" ]
}

log "守护启动: 短轮询 ${POLL_WAIT}s+间隙 ${POLL_GAP}s, 被动每 ${PASSIVE_MIN}min, 窗口 ${WORK_DAYS} ${WORK_START}~${WORK_END}, 设备=$DEVICE_NAME"

sleeping=0
while true; do
  # --- 工作窗口: 窗口外休眠 (10 分钟醒来看表, 不耗任何服务端额度) ---
  if ! in_window; then
    if [ "$sleeping" = 0 ]; then
      log "进入休眠: 窗口外 (配置 ${WORK_DAYS} ${WORK_START}~${WORK_END}), 到点自动恢复"
      sleeping=1
    fi
    sleep 600
    continue
  fi
  if [ "$sleeping" = 1 ]; then
    log "进入工作窗口, 恢复轮询"
    sleeping=0
  fi

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

  # --- 短轮询命令通道: 服务端最多挂 POLL_WAIT 秒即返回 ---
  resp=$(curl -sS --get -m $((POLL_WAIT + 10)) -w '\n%{http_code}' \
    -H "X-Device-Key: $DEVICE_KEY" \
    --data-urlencode "device=$DEVICE_NAME" \
    -d "wait=$POLL_WAIT" \
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
      : # 短挂超时, 无命令, 正常 → 进入间隙
      ;;
    000)
      sleep 10
      ;;
    *)
      log "poll 异常 HTTP $code, 休 10s"
      sleep 10
      ;;
  esac
  sleep "$POLL_GAP"
done
