# REA Web · 本地 APK 逆向分析交互界面

拖拽上传 APK → 自动反编译（jadx）→ 在浏览器里浏览反编译出的 Java 源码。
所有处理均在本机完成，文件不上传任何服务器。

## 功能

- 📦 拖拽 / 点击上传 `.apk`，三步进度实时展示（上传 → 反编译 → 索引）
- 🗂 按包名层级浏览文件树，支持类名 / 文件名搜索
- 📝 代码查看器，Java 语法高亮
- 🔧 反混淆（`--deobf`）、4 线程反编译，部分类失败不影响整体结果

## 环境要求

- Node.js ≥ 18
- Windows（jadx 自带便携 JRE，无需安装 Java）
- macOS / Linux：需自行安装 Java 11+ 并通过 `JADX_JAVA` / `JADX_JAR` 环境变量指向 jadx CLI

## 快速开始

```bash
# 1. 下载工具链（jadx 1.5.6 + apktool 3.0.3，约 115MB）
bash scripts/setup-tools.sh        # Git Bash / Linux / macOS
# 或 PowerShell：
# powershell -File scripts/setup-tools.ps1

# 2. 启动
npm start                          # 等价于 node server.js

# 3. 打开浏览器
# http://127.0.0.1:8787
```

## 目录结构

```
rea-web/
├── server.js                 # 零依赖 Node 后端（上传 / 反编译 / 文件树 / 源码 API）
├── public/
│   └── index.html            # 单页前端
├── scripts/
│   ├── setup-tools.sh        # 工具链下载脚本（bash）
│   └── setup-tools.ps1       # 工具链下载脚本（PowerShell）
└── tools/                    # （git 忽略）setup 后生成：jadx + apktool
```

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/analyze` | 上传 APK（body 为文件二进制，文件名放 `X-Filename` header），返回 `{ jobId }` |
| GET | `/api/job/:id` | 查询分析状态：`uploaded / analyzing / done / error` |
| GET | `/api/tree/:id` | 获取反编译输出的文件树 |
| GET | `/api/file/:id?path=...` | 读取单个反编译文件内容（≤2MB） |

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `8787` | 监听端口 |
| `JADX_JAVA` | `tools/jadx/jre/bin/java.exe` | jadx 使用的 java 可执行文件 |
| `JADX_JAR` | `tools/jadx/lib/jadx-gui-1.5.6-all.jar` | jadx all-in-one jar |

## 说明

- 反编译大型 App（数千个类）约需 30–60 秒；个别类反编译失败属于正常现象（jadx 会跳过并继续）。
- 仅供学习研究使用，请遵守目标应用的相关法律法规与许可协议。
- 上游工具：[jadx](https://github.com/skylot/jadx) · [Apktool](https://github.com/iBotPeaches/Apktool)
