# 听书 · 小说朗读（Android）

一个原创的本地小说朗读 App：导入 TXT → 自动分章分句 → 逐句朗读并高亮 → 语速/音色/定时/进度记忆。
所有文本都在本机处理，不联网、不上传，也不包含任何第三方应用的代码、接口或内容。

## 功能

- 📚 书架：内置原创示例小说 + 导入本地 TXT（自动识别 UTF-8 / GBK 编码）
- 📖 章节解析：识别「第X章/回/卷」等标题，无标题时按长度自动分段；目录跳转、上下章
- 🔊 逐句朗读：按标点切句，每句排入 TTS 队列，播放回调驱动**句子高亮 + 自动滚动**
- 🎛 控制：0.5x–2.0x 语速、可用中文音色切换、15/30/60 分钟定时停止、自动续读下一章
- 💾 进度：按书保存"第几章第几句"，重进自动恢复
- 🗣 语音引擎：优先使用手机上安装的讯飞语音引擎（讯飞音色），未安装则回退系统默认；书架页可手动切换
- 📱 阅读页（仿主流中文阅读 App 的沉浸式布局，独立实现，未使用任何第三方图标/素材）：
  - 点击正文区域显隐工具栏；顶部：返回 / 书名章节 / 更多
  - 底部：本章进度条 + 目录、背景、设置、朗读、定时
  - 设置面板：字号（14–30sp）、行距（三档）、5 套背景主题（羊皮纸/纯白/护眼/浅灰/夜间）、亮度
  - 朗读条：开始/暂停、上句/下句、语速、音色、定时
  - 页脚显示章数、句数与本章进度；所有设置自动记忆

## 关于「QQ阅读同款语音」

QQ阅读的朗读模块使用讯飞定制音色，属商业授权资源，无法也不应直接从其 APK 中提取复用。
本项目的做法是：通过 Android 标准 `TextToSpeech` API 调用设备上已安装的**讯飞语音引擎**，
从而合法获得讯飞音色；未安装时自动使用系统 TTS。

`TtsEngine.java` 封装了独立的一层接口（init / speak / setVoice / setSpeed / 逐句回调），
若后续在讯飞开放平台注册了自己的应用并拿到 APPID/密钥，可直接替换为官方 SDK 实现，
无需改动界面与解析层。

## 构建

需要 JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.7。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

本项目使用纯 Android 框架控件（无 AndroidX、无第三方依赖），便于离线构建与审计。

## 目录结构

```
app/src/main/
├── java/com/example/novelreader/
│   ├── MainActivity.java      书架 / 导入 / 引擎选择
│   ├── ReaderActivity.java    阅读 / 朗读 / 高亮 / 控制
│   ├── TtsEngine.java         TTS 引擎封装（可插拔）
│   ├── ChapterParser.java    分章 + 分句 + 字符偏移
│   ├── BookStore.java         书架数据 / 文本读取 / 进度
│   └── Book.java Chapter.java Sentence.java
├── res/layout/                界面
└── assets/sample_novel.txt    内置原创示例小说（可自由使用）
```

## 合规说明

- 内置示例为本项目原创文本，可自由使用。
- 阅读页参照中文阅读 App 常见的交互范式独立编写，图标均用 Unicode 字符，未复制任何第三方图片、图标、品牌元素或布局文件。
- 用户自行导入的文本由其本人负责；应用不提供任何在线书源、破解或绕过付费内容的能力。
- 仅供学习与技术验证使用。
