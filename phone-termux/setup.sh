#!/data/data/com.termux/files/usr/bin/bash
# 一键安装 · 在 Termux 中执行: bash setup.sh
# v2.4: 免费额度省费 (短轮询+间隙+工作窗口); v2.2: agent 统一守护 + cron 仅做看门狗
set -euo pipefail

BASEDIR=$(cd "$(dirname "$0")" && pwd)
CONFIG="$BASEDIR/config.env"

echo "==> [1/8] 安装依赖 (termux-api jq curl cronie)"
yes | pkg update >/dev/null 2>&1 || true
pkg install -y termux-api jq curl cronie >/dev/null

echo "==> [2/8] 检查 Termux:API 配套 App"
if ! termux-location -p network -t 5 >/dev/null 2>&1; then
  echo "    [警告] termux-location 不可用!"
  echo "    请从 F-Droid 安装 Termux:API App 后重新运行本脚本"
  echo "    F-Droid: https://f-droid.org/packages/com.termux.api/"
  exit 1
fi

echo "==> [3/8] 写入配置"
if [ -f "$CONFIG" ]; then
  echo "    已存在 config.env, 跳过 (如需修改请手动编辑)"
else
  read -rp "    服务端地址 API_BASE (如 https://your-app.vercel.app): " API_BASE
  API_BASE="${API_BASE%%/}"
  read -rp "    设备密钥 DEVICE_KEY: " DEVICE_KEY
  read -rp "    设备名称 DEVICE_NAME [redmi-note15pro]: " DEVICE_NAME
  DEVICE_NAME="${DEVICE_NAME:-redmi-note15pro}"
  [ -n "$API_BASE" ] && [ -n "$DEVICE_KEY" ] || { echo "API_BASE/DEVICE_KEY 不能为空"; exit 1; }
  umask 077
  cat > "$CONFIG" <<EOF
API_BASE='$API_BASE'
DEVICE_KEY='$DEVICE_KEY'
DEVICE_NAME='$DEVICE_NAME'
EOF
fi

read -rp "    被动上报间隔(分钟, 5~1440) [60]: " PASSIVE_MIN
PASSIVE_MIN="${PASSIVE_MIN:-60}"
[[ "$PASSIVE_MIN" =~ ^[0-9]+$ ]] && [ "$PASSIVE_MIN" -ge 5 ] && [ "$PASSIVE_MIN" -le 1440 ] || { echo "间隔须为 5~1440 分钟"; exit 1; }

echo "    --> 免费额度省费: 短轮询 + 工作窗口 (v2.4)"
read -rp "    轮询挂线秒数(1~5, 服务端硬限 5) [5]: " POLL_WAIT
POLL_WAIT="${POLL_WAIT:-5}"
[[ "$POLL_WAIT" =~ ^[1-5]$ ]] || { echo "挂线须为 1~5 秒"; exit 1; }
read -rp "    轮询间隙秒数(5~120, 越大越省额度, 定位到点越慢) [25]: " POLL_GAP
POLL_GAP="${POLL_GAP:-25}"
[[ "$POLL_GAP" =~ ^[0-9]+$ ]] && [ "$POLL_GAP" -ge 5 ] && [ "$POLL_GAP" -le 120 ] || { echo "间隙须为 5~120 秒"; exit 1; }
valid_hhmm() { [[ "$1" =~ ^([01][0-9]|2[0-3])[0-5][0-9]$ ]]; }
read -rp "    工作窗口开始 HHMM [0800]: " WORK_START
WORK_START="${WORK_START:-0800}"
valid_hhmm "$WORK_START" || { echo "开始时间须为 HHMM (0000~2359)"; exit 1; }
read -rp "    工作窗口结束 HHMM [1800]: " WORK_END
WORK_END="${WORK_END:-1800}"
valid_hhmm "$WORK_END" || { echo "结束时间须为 HHMM (0000~2359)"; exit 1; }
[ "$WORK_START" -lt "$WORK_END" ] || { echo "开始须早于结束"; exit 1; }
read -rp "    工作日(1=周一...7=周日, 支持 1-5 / 1,3,5) [1-5]: " WORK_DAYS
WORK_DAYS="${WORK_DAYS:-1-5}"
[[ "$WORK_DAYS" =~ ^[0-9,-]+$ ]] || { echo "工作日格式: 1-5 或 1,3,5"; exit 1; }

grep -q '^PASSIVE_MIN=' "$CONFIG" 2>/dev/null || echo "PASSIVE_MIN='$PASSIVE_MIN'" >> "$CONFIG"
grep -q '^POLL_WAIT=' "$CONFIG" 2>/dev/null || echo "POLL_WAIT='$POLL_WAIT'" >> "$CONFIG"
grep -q '^POLL_GAP=' "$CONFIG" 2>/dev/null || echo "POLL_GAP='$POLL_GAP'" >> "$CONFIG"
grep -q '^WORK_START=' "$CONFIG" 2>/dev/null || echo "WORK_START='$WORK_START'" >> "$CONFIG"
grep -q '^WORK_END=' "$CONFIG" 2>/dev/null || echo "WORK_END='$WORK_END'" >> "$CONFIG"
grep -q '^WORK_DAYS=' "$CONFIG" 2>/dev/null || echo "WORK_DAYS='$WORK_DAYS'" >> "$CONFIG"
chmod 600 "$CONFIG"

# shellcheck source=/dev/null
source "$CONFIG"

echo "==> [4/8] 测试服务端连通性"
code=$(curl -s -o /dev/null -w '%{http_code}' -m 15 "$API_BASE/api/report" 2>/dev/null || echo 000)
if [ "$code" = "405" ] || [ "$code" = "401" ]; then
  echo "    服务端可达 (HTTP $code)"
else
  echo "    [警告] 服务端返回 $code (期望 405/401), 请检查 API_BASE 和部署状态"
  read -rp "    仍然继续? [y/N] " yn
  [[ "$yn" =~ ^[Yy]$ ]] || exit 1
fi

echo "==> [5/8] 配置看门狗定时任务 (cron 只负责保活, 不负责上报)"
WATCHDOG_LINE="*/15 * * * * bash '$BASEDIR/watchdog.sh' >> '$BASEDIR/watchdog.log' 2>&1"
(crontab -l 2>/dev/null | grep -vE "(report\.sh|watchdog\.sh)"; echo "$WATCHDOG_LINE") | crontab -
pgrep crond >/dev/null 2>&1 || crond
echo "    已注册: $WATCHDOG_LINE"

echo "==> [6/8] 启动 agent 统一守护 (长轮询 + 被动上报)"
if pgrep -f "agent\.sh" >/dev/null 2>&1; then
  echo "    agent 已在运行, 跳过启动"
else
  nohup bash "$BASEDIR/agent.sh" >/dev/null 2>&1 &
  sleep 2
  pgrep -f "agent\.sh" >/dev/null 2>&1 && echo "    agent 已启动 (PID $(pgrep -f 'agent\.sh' | head -n1))" || echo "    [警告] agent 启动失败, 查看 agent.log"
fi

echo "==> [7/8] 配置开机自启 (Termux:Boot)"
BOOT_DIR="$HOME/.termux/boot"
if [ -d "$BOOT_DIR" ] || [ -d "$HOME/.termux" ]; then
  mkdir -p "$BOOT_DIR"
  cat > "$BOOT_DIR/phone-loc.sh" <<EOF
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
crond
nohup bash "$BASEDIR/agent.sh" >/dev/null 2>&1 &
EOF
  chmod +x "$BOOT_DIR/phone-loc.sh"
  echo "    已写入 ~/.termux/boot/phone-loc.sh (开机: 唤醒锁 + crond + agent)"
  command -v termux-boot >/dev/null 2>&1 || \
  [ ! -d /data/data/com.termux.boot ] && \
  echo "    [提示] 建议安装 Termux:Boot (F-Droid) 并打开一次, 开机自启才生效"
else
  echo "    [警告] 未检测到 Termux:Boot, 重启后需手动打开 Termux 运行 setup.sh"
fi
termux-wake-lock
echo "    已开启唤醒锁 (termux-wake-lock)"

echo "==> [8/8] 立即试运行一次上报"
if bash "$BASEDIR/report.sh"; then
  echo ""
  echo "=============================================================="
  echo " 安装完成! 双模式 (免费额度省费版):"
  echo " · 被动: agent 每 $PASSIVE_MIN 分钟上报一次(时间制, 不受 Doze 影响)"
  echo " · 主动: 浏览器地图点[获取位置] → 约 0.5~1 分钟出最新位置 (轮询 ${POLL_WAIT}s 挂线 + ${POLL_GAP}s 间隙)"
  echo " · 窗口: 仅 $WORK_DAYS 的 $WORK_START~$WORK_END 运行, 其余时间休眠 0 额度 (地图显示失联属预期)"
  echo " · 看门狗: cron 每 15 分钟检查 agent, 死了自动拉起"
  echo " 浏览器打开: $API_BASE/map?token=你的访问令牌"
  echo " 下一步: 按 HYPEROS-保活清单.md 逐项设置手机(必须!)"
  echo " 日志: report.log(上报) / agent.log(守护) / watchdog.log(自愈)"
  echo "=============================================================="
else
  echo "    [警告] 试运行失败, 查看日志: $BASEDIR/report.log"
fi
