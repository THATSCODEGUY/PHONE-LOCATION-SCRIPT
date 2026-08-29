#!/data/data/com.termux/files/usr/bin/bash
# cron 看门狗 · 每 15 分钟由 crontab 调用 (setup.sh 注册)
# 检查 agent 统一守护是否存活, 死了拉起自愈
BASEDIR=$(cd "$(dirname "$0")" && pwd)

if ! pgrep -f "agent\.sh" >/dev/null 2>&1; then
  nohup bash "$BASEDIR/agent.sh" >/dev/null 2>&1 &
  echo "[$(date '+%F %T')] agent 已死, 已重启" >> "$BASEDIR/watchdog.log"
fi
