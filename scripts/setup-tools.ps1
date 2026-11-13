# 下载 jadx (含便携 JRE) 和 apktool 到 tools/ 目录（PowerShell 版）
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force -Path tools | Out-Null

$JADX_VER = "1.5.6"
$APKTOOL_VER = "3.0.3"

if (-not (Test-Path "tools/jadx/lib/jadx-gui-$JADX_VER-all.jar")) {
  Write-Host "==> 下载 jadx-gui $JADX_VER (with JRE)..."
  Invoke-WebRequest -Uri "https://github.com/skylot/jadx/releases/download/v$JADX_VER/jadx-gui-$JADX_VER-with-jre-win.zip" -OutFile "tools/jadx-with-jre.zip"
  Write-Host "==> 解压..."
  Expand-Archive -Force "tools/jadx-with-jre.zip" "tools/jadx"
} else {
  Write-Host "==> jadx 已就绪，跳过"
}

if (-not (Test-Path "tools/apktool.jar")) {
  Write-Host "==> 下载 apktool $APKTOOL_VER..."
  Invoke-WebRequest -Uri "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar" -OutFile "tools/apktool.jar"
} else {
  Write-Host "==> apktool 已就绪，跳过"
}

Write-Host "==> 完成。运行 npm start 后访问 http://127.0.0.1:8787"
