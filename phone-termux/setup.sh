#!/data/data/com.termux/files/usr/bin/bash
# 一键安装 · 在 Termux 中执行: bash setup.sh
set -euo pipefail

BASEDIR=$(cd "$(dirname "$0")" && pwd)
CONFIG="$BASEDIR/config.env"

echo "==> [1/7] 安装依赖 (termux-api jq curl cronie)"
yes | pkg update >/dev/null 2>&1 || true
pkg install -y termux-api jq curl cronie >/dev/null

echo "==> [2/7] 检查 Termux:API 配套 App"
if ! termux-location -p network -t 5 >/dev/null 2>&1; then
  echo "    [警告] termux-location 不可用!"
  echo "    请从 F-Droid 安装 Termux:API App 后重新运行本脚本"
  echo "    F-Droid: https://f-droid.org/packages/com.termux.api/"
  exit 1
fi

echo "==> [3/7] 写入配置"
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

# shellcheck source=/dev/null
source "$CONFIG"

echo "==> [4/7] 测试服务端连通性"
code=$(curl -s -o /dev/null -w '%{http_code}' -m 15 "$API_BASE/api/report" 2>/dev/null || echo 000)
if [ "$code" = "405" ] || [ "$code" = "401" ]; then
  echo "    服务端可达 (HTTP $code)"
else
  echo "    [警告] 服务端返回 $code (期望 405/401), 请检查 API_BASE 和部署状态"
  read -rp "    仍然继续? [y/N] " yn
  [[ "$yn" =~ ^[Yy]$ ]] || exit 1
fi

echo "==> [5/7] 配置定时任务 (cron)"
read -rp "    上报间隔分钟 [5]: " INTERVAL
INTERVAL="${INTERVAL:-5}"
[[ "$INTERVAL" =~ ^[0-9]+$ ]] && [ "$INTERVAL" -ge 1 ] && [ "$INTERVAL" -le 59 ] || { echo "间隔须为 1-59"; exit 1; }
CRON_LINE="*/$INTERVAL * * * * bash '$BASEDIR/report.sh' >> '$BASEDIR/report.log' 2>&1"
(crontab -l 2>/dev/null | grep -v "report.sh"; echo "$CRON_LINE") | crontab -
pgrep crond >/dev/null 2>&1 || crond
echo "    已注册: $CRON_LINE"

echo "==> [6/7] 配置开机自启 (Termux:Boot)"
BOOT_DIR="$HOME/.termux/boot"
if [ -d "$BOOT_DIR" ] || [ -d "$HOME/.termux" ]; then
  mkdir -p "$BOOT_DIR"
  cat > "$BOOT_DIR/phone-loc.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
crond
EOF
  chmod +x "$BOOT_DIR/phone-loc.sh"
  echo "    已写入 ~/.termux/boot/phone-loc.sh (开机: 唤醒锁 + crond)"
  command -v termux-boot >/dev/null 2>&1 || \
  [ ! -d /data/data/com.termux.boot ] && \
  echo "    [提示] 建议安装 Termux:Boot (F-Droid) 并打开一次, 开机自启才生效"
else
  echo "    [警告] 未检测到 Termux:Boot, 重启后需手动打开 Termux 运行: crond"
fi
termux-wake-lock
echo "    已开启唤醒锁 (termux-wake-lock)"

echo "==> [7/7] 立即试运行一次上报"
if bash "$BASEDIR/report.sh"; then
  echo ""
  echo "=============================================="
  echo " 安装完成! 浏览器打开:"
  echo " $API_BASE/map?token=你的访问令牌"
  echo " 下一步: 按 HYPEROS-保活清单.md 逐项设置手机"
  echo " 日志: tail -f $BASEDIR/report.log"
  echo "=============================================="
else
  echo "    [警告] 试运行失败, 查看日志: $BASEDIR/report.log"
fi
