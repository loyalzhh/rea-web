#!/usr/bin/env bash
# 下载 jadx (含便携 JRE) 和 apktool 到 tools/ 目录
set -e
cd "$(dirname "$0")/.."
mkdir -p tools

JADX_VER="1.5.6"
APKTOOL_VER="3.0.3"

if [ ! -f "tools/jadx/lib/jadx-gui-${JADX_VER}-all.jar" ]; then
  echo "==> 下载 jadx-gui ${JADX_VER} (with JRE)..."
  curl -L -o tools/jadx-with-jre.zip \
    "https://github.com/skylot/jadx/releases/download/v${JADX_VER}/jadx-gui-${JADX_VER}-with-jre-win.zip"
  echo "==> 解压..."
  unzip -q -o tools/jadx-with-jre.zip -d tools/jadx
else
  echo "==> jadx 已就绪，跳过"
fi

if [ ! -f "tools/apktool.jar" ]; then
  echo "==> 下载 apktool ${APKTOOL_VER}..."
  curl -L -o tools/apktool.jar \
    "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VER}/apktool_${APKTOOL_VER}.jar"
else
  echo "==> apktool 已就绪，跳过"
fi

echo "==> 完成。运行 npm start 后访问 http://127.0.0.1:8787"
