#!/data/data/com.termux/files/usr/bin/bash
# 一键安装引导 · 公有 GitHub 直链下载
# 仓库地址已配置: THATSCODEGUY/PHONE-LOCATION-SCRIPT (若换仓库/改名, 同步修改下面 REPO_BASE)
# 手机 Termux 里运行(整行一条命令):
#   bash -c "$(curl -sSL https://raw.githubusercontent.com/THATSCODEGUY/PHONE-LOCATION-SCRIPT/main/phone-termux/install.sh)"
set -euo pipefail

REPO_BASE='https://raw.githubusercontent.com/THATSCODEGUY/PHONE-LOCATION-SCRIPT/main'
DL="$REPO_BASE/phone-termux"
DIR="$HOME/phone-loc"

echo "==> [1/4] 创建目录 $DIR"
mkdir -p "$DIR"

echo "==> [2/4] 从 GitHub 下载脚本"
dl() {
  local n="$1"
  echo "    下载 $n ..."
  curl -fsSL -m 60 -o "$DIR/$n" "$DL/$n" || { echo "    下载失败: $n"; exit 1; }
  [ -s "$DIR/$n" ] || { echo "    文件为空: $n"; exit 1; }
}
for f in report.sh setup.sh agent.sh watchdog.sh install.sh; do
  dl "$f"
done
curl -fsSL -m 60 -o "$DIR/HYPEROS-保活清单.md" "$DL/HYPEROS-%E4%BF%9D%E6%B4%BB%E6%B8%85%E5%8D%95.md" \
  || echo "    清单下载失败(不影响安装)"

echo "==> [3/4] 设置执行权限"
chmod +x "$DIR"/*.sh

echo "==> [4/4] 启动交互安装"
echo ""
echo "=============================================================="
echo " 下载校验完成, 开始安装..."
echo "=============================================================="
exec bash "$DIR/setup.sh"
