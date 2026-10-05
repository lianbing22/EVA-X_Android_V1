# EVA-X Android V1 — 具身桌面 AI 伴侣与双 Agent 跨网指挥终端

[![Release](https://img.shields.io/badge/Release-v1.3.0-00E5FF?style=flat-square)](https://github.com/lianbing22/EVA-X_Android_V1/releases/tag/v1.3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%20API%2026%2B-3DDC84?style=flat-square)](https://github.com/lianbing22/EVA-X_Android_V1)
[![Router](https://img.shields.io/badge/Router-Jev%204--Tier%20%2B%20Dual%20Agent-7C4DFF?style=flat-square)](https://github.com/lianbing22/EVA-X_Android_V1)

**EVA-X Android V1** 是一款采用纯黑 OLED 极简发光眼设计（灵感源自 Anki Cozmo、RoboEyes 与钉钉 QwenNote Eva 桌面伴侣模式）的 Android 具身 AI 语音伴侣应用。它不仅拥有丰富的**主动聆听反馈**、**可信任务终态反应**与 **8 场本地待机小剧场**，还能通过加密跨网隧道连接您的 Mac 电脑网关，借助 **Jev 四级智能路由**直接对话高速大模型，或远程指挥您电脑上的 **Antigravity CLI Agent (`agy`)** 与 **WorkBuddy 桌面助理**干活。

---

## 📜 历次 Git 提交全记录（Every Commit History）

仓库每一次提交的功能演进、对应时间、Commit Hash 与核心改动均完整记录在下表中：

| 序号 | Commit Hash | 提交时间 (UTC+8) | 提交标题 (Commit Message) | 核心演进与详细改动说明 |
| :---: | :--- | :--- | :--- | :--- |
| **11** | *(当前提交)* | `2026-10-05 18:56` | `docs(readme): 全面更新中文仓库说明并附上每一次 Git 提交完整演进表` | 将 `README.md` 升级为完整中文架构与使用文档，内嵌从首版至今每一次 Git 提交的完整对照表，并同步更新 GitHub Release 说明。 |
| **10** | [`1507995`](https://github.com/lianbing22/EVA-X_Android_V1/commit/1507995eb0fa22193fc848cb1431b608edbdbe65) | `2026-10-05 18:50` | `feat(behavior): complete 8 idle stories, listening stages, verified task feedback & TTS deferred queue` | 完整落地《EVA-X：反馈动作、主动聆听与待机小剧场》方案：实现 8 场本地 Canvas 待机小剧场、6 阶段主动聆听反馈、严格区分 `ReplyReady` 与 `TaskSucceeded` 可信任务终态动作、TTS 反应期间缓播队列及勿扰无障碍标签修复。 |
| **9** | [`d6fe4ae`](https://github.com/lianbing22/EVA-X_Android_V1/commit/d6fe4ae7b29ef828b5b70bf25040bfdbeb52315f) | `2026-10-05 18:13` | `feat: add direct Antigravity CLI Agent (agy) & WorkBuddy Agent command routing and quick actions` | 新增对本机 **Antigravity CLI (`agy -p --mode agent`)** 与 **WorkBuddy Local Agent** 的双 Agent 实操指挥支持；底部新增「指挥 Antigravity 查讯飞录音」「指挥 Antigravity 查桌面文件」「指挥 WorkBuddy 查任务」快捷芯片。 |
| **8** | [`f687912`](https://github.com/lianbing22/EVA-X_Android_V1/commit/f687912334603ff6259e3bd8094d1249db10c540) | `2026-10-05 16:10` | `feat: live speech-to-text subtitle & Jev + Antigravity Tools (gemini-3.8-flash-low / 2.5-flash) smart streaming router` | 新增主界面金色「🎙 实时语音转写」流式字幕横幅（边说边出字）；接入本机 **Antigravity Tools (`127.0.0.1:8045`)** 反代与 **Jev 路由评估体系**，使用 `gemini-2.5-flash`（~1.58s 首包）与 `gemini-3.8-flash-low` 实现毫秒级流式语音对话。 |
| **7** | [`9564dab`](https://github.com/lianbing22/EVA-X_Android_V1/commit/9564dab540a97f34e84c59ba376d9171625f2459) | `2026-10-05 15:30` | `fix(gateway): default APK config to active public tunnel and sync resolved endpoint on test` | 将默认网关地址指向活动外网 HTTPS 隧道，并在点击「测试连接」通过信标自动寻址后，自动将解析出的最新隧道 URL 同步回输入框与本地配置。 |
| **6** | [`1f3a5e2`](https://github.com/lianbing22/EVA-X_Android_V1/commit/1f3a5e23f408126e12c822083da5e67a75e8a33a) | `2026-10-05 13:33` | `feat(gateway): implement scheme 1 outbound HTTPS tunnel with AES-256-GCM pairing beacon discovery` | 实现跨局域网（5G/异地 Wi-Fi）外网长连接方案 1：电脑网关主动发起 outbound HTTPS 隧道，并将隧道地址用配对码（PBKDF2 + AES-256-GCM）加密发布到发现信标；手机端仅凭固定配对码即可免重装自动寻址重连。 |
| **5** | [`e539984`](https://github.com/lianbing22/EVA-X_Android_V1/commit/e539984487e3c96e71225d33f3b3a6ab6c4f92be) | `2026-10-04 13:48` | `feat(workbuddy): complete real local WorkBuddy Gateway Protocol integration, auto-pairing & file-read verification` | 完成真实本地 WorkBuddy Gateway Protocol 接入、自动加密配对（Ed25519/Bearer Token）、在线健康检查、取消自动演示兜底，并通过读取桌面测试文件完成端到端控制验收。 |
| **4** | [`290b69b`](https://github.com/lianbing22/EVA-X_Android_V1/commit/290b69b3e1fa9a25ebbd2254c398f06ca013c6b9) | `2026-10-03 23:22` | `feat(bridge): connect EVA-X to Mac DSH & WorkBuddy gateway with streaming TTS and 1.25x speech rate` | 打通 Android 客户端到 Mac 网关的 NDJSON 流式事件链路，实现流式分句 TTS 边收边读（首句到达立即朗读，支持 1.25x 倍速调节）。 |
| **3** | [`c0a589f`](https://github.com/lianbing22/EVA-X_Android_V1/commit/c0a589fa431257ff5ca512516fb89d01f1f4048a) | `2026-10-03 22:36` | `fix: continuous face-tracking gaze across all states/modes and triple-fallback real speech recognition` | 修复所有状态与横竖屏模式下的连续人脸视线追踪（Gaze Bias），并实现 Android 原生语音识别的三重降级回退机制（内置识别器 → 系统识别服务 → 语音输入面板）。 |
| **2** | [`6c7f297`](https://github.com/lianbing22/EVA-X_Android_V1/commit/6c7f297761139c7026a9216d3e71b50ff66e1168) | `2026-10-03 22:14` | `feat: add DingTalk QwenNote Eva landscape mode, 3 eye styles, 3 companion states, and front-camera real-time face tracking gaze` | 新增横屏桌面伴侣模式（DingTalk QwenNote Eva 风格）、3 种眼型（赛博方圆眼 / 星萌圆眸 / 机甲锐角眼）、3 种伴侣状态（专注盯人 / 好奇灵动 / 深夜微光）及前置摄像头 CameraX + ML Kit 实时人脸追踪。 |
| **1** | [`e5ea1fe`](https://github.com/lianbing22/EVA-X_Android_V1/commit/e5ea1fe299b31dc0d727653194be41008cb085aa) | `2026-10-03 21:55` | `feat: EVA-X Android V1 embodied desk companion UI (Cozmo/DeskMate OLED HUD)` | 项目初始版本：构建 Cozmo/DeskMate 风格纯黑 OLED HUD 界面、Compose Canvas 发光双眼渲染、弹簧物理视线动画与基础语音交互框架。 |

---

## ✨ 核心特性一览

### 1. 连续行为语言：主动聆听、可信结果反馈与 8 场待机小剧场
- **固定主角舞台**：双眼布局始终稳定居于主舞台，道具仅短暂出现在眼睛下方，不挤压或遮挡主界面文案与控制区。
- **6 阶段主动聆听**：
  - `ListeningStart`（微倾侧耳，光晕柔和呼吸）
  - `SpeechDetected`（检测到人声，视线聚焦）
  - `ShortPauseAck`（说话短停顿轻量点头回应，带 2.5s 冷却与单轮上限）
  - `Recognizing`（识别收敛）
  - `Understood`（理解确认小点头）
  - `Executing`（执行任务微扫视）
- **实时语音转文字字幕 (`🎙 实时语音转写`)**：说话过程中利用 `onPartialResults` 在主界面金色胶囊横幅中实时滚动显示已识别的文字，无需等说完才看到识别内容。
- **严格区分「回复就绪」与「任务真正完成」**：
  - **普通聊天回复结束 (`ReplyReady`)**：抬眼看用户并轻轻点头，绝不虚假庆祝。
  - **电脑任务可信完成 (`TaskSucceeded`)**：仅当收到网关带 `taskId` 与 `resultEvidence` 的真实完成终态时，才触发 ~1.2 秒的三变体开心庆祝动作（看向用户 → 小点头 → 开心笑眼 → 回视），并在前 700ms 缓播 TTS 防止动作与语音打架。
  - **需要确认 / 明确失败 / 断线不明 / 取消确认**：分别对应好奇歪头、短暂委屈恢复专注、困惑歪头、平静点头收起视觉。
- **8 场本地 Canvas 待机小剧场 (`IdleScene`)**：
  - 空闲满 25 秒（且非勿扰、非低电量、距上次剧场 ≥ 90 秒）时自动上演，并在设置面板提供一键预览：
    1. `WebSurfing`（网上冲浪）
    2. `ReadingBook`（翻阅小书）
    3. `CatchingStar`（接住流星）
    4. `MusicNodding`（戴耳机听歌晃脑）
    5. `ChasingPixelBug`（追逐像素小虫）
    6. `SippingEnergyDrink`（喝能量饮料）
    7. `PaperPlane`（放飞纸飞机）
    8. `DozingOff`（打瞌睡惊醒）
  - **毫秒级打断**：任何语音开始、屏幕触摸、发送任务或新状态变更，会在 120ms 内收起道具并回到交互状态。

### 2. Jev 四级智能路由 + 双 Agent 电脑实操控制
电脑端桥接网关（`evax-workbuddy-bridge`）内置基于 **Jev 复杂度评估思想**的四级路由器（`router.mjs`），根据您在手机上说的话自动分发：

| 路由档位 | 触发方式 / 识别规则 | 底层执行引擎 | 响应速度与能力边界 |
| :--- | :--- | :--- | :--- |
| **Tier 1: `flash_ultra`** | 日常寒暄、简短问答、时间/天气查询 | 本机 Antigravity Tools 反代 `gemini-2.5-flash` (+ 300ms 实时天气注入) | **TTFT ~1.58 秒**，极速流式秒回 + 边收边读 |
| **Tier 2: `flash_3_8`** | 深度推理、方案规划、写代码、长文分析 | 本机 Antigravity Tools 反代 `gemini-3.8-flash-low` | **TTFT ~2.4 秒**，高智商深度回答 |
| **Tier 3: `agy_agent`** | 说出/输入 **“让 Antigravity ...”**、**“查看讯飞录音”**、**“看下电脑桌面文件”** | 本机 `/Users/lianb/.local/bin/agy -p --mode agent` (**Antigravity CLI Agent**) | **真实操控电脑**：调用本机 Skills（如 `xftj-transcribe` 讯飞听见）、读写文件、执行终端命令、操控浏览器，并实时回传执行步骤 |
| **Tier 4: `workbuddy_agent`** | 说出/输入 **“让 WorkBuddy ...”** | 本机 `WorkBuddy Local Daemon` (`127.0.0.1:48391`) | 调用 WorkBuddy 桌面助理执行本地办公任务 |

### 3. 跨网（5G / 异地 Wi-Fi）加密信标自动寻址
- 电脑网关启动时自动建立外网 HTTPS 隧道，并用配对码派生密钥（`PBKDF2-HMAC-SHA256` + `AES-256-GCM`）将最新公网地址加密发布到发现信标。
- 手机端无论在蜂窝移动网络还是异地 Wi-Fi，只需配置一次固定配对码（如 `EVAX-WB-2026`），即可自动解密拉取最新公网隧道地址，无需重装 APK 或手动改 IP。

---

## 📂 代码目录结构

- [`app/src/main/java/com/evax/mobile/MainActivity.kt`](app/src/main/java/com/evax/mobile/MainActivity.kt) — 应用入口与权限管理
- [`app/src/main/java/com/evax/mobile/ui/ConversationScreen.kt`](app/src/main/java/com/evax/mobile/ui/ConversationScreen.kt) — 主界面 HUD、实时语音转写字幕横幅、双 Agent 快捷指令栏与连接设置弹窗
- [`app/src/main/java/com/evax/mobile/ui/AssistantAvatar.kt`](app/src/main/java/com/evax/mobile/ui/AssistantAvatar.kt) — 极简发光眼 Canvas 渲染、表情弹簧物理动画与人脸追踪偏移合成
- [`app/src/main/java/com/evax/mobile/ui/CompanionBehaviorModel.kt`](app/src/main/java/com/evax/mobile/ui/CompanionBehaviorModel.kt) — 主动聆听阶段、可信结果反馈脚本与行为状态机定义
- [`app/src/main/java/com/evax/mobile/ui/CompanionBehaviorCoordinator.kt`](app/src/main/java/com/evax/mobile/ui/CompanionBehaviorCoordinator.kt) — 行为调度器、短停顿点头冷却、待机小剧场触发与即时打断逻辑
- [`app/src/main/java/com/evax/mobile/ui/IdleScene.kt`](app/src/main/java/com/evax/mobile/ui/IdleScene.kt) & [`IdleSceneProps.kt`](app/src/main/java/com/evax/mobile/ui/IdleSceneProps.kt) — 8 场待机小剧场时间轴与眼下极简矢量道具绘制
- [`app/src/main/java/com/evax/mobile/bridge/PcAgentBridge.kt`](app/src/main/java/com/evax/mobile/bridge/PcAgentBridge.kt) — 跨网信标解密寻址、NDJSON 流式事件解析、旧任务事件隔离与证据校验
- [`app/src/main/java/com/evax/mobile/speech/SpeechServices.kt`](app/src/main/java/com/evax/mobile/speech/SpeechServices.kt) — 三重降级语音识别、实时 `onPartialResults` 回调、流式分句 TTS 与 700ms 反应缓播队列
- [`app/src/main/java/com/evax/mobile/vision/FrontCameraFaceTracker.kt`](app/src/main/java/com/evax/mobile/vision/FrontCameraFaceTracker.kt) — CameraX + ML Kit 前置摄像头实时人脸追踪

---

## 🛠️ 构建与测试指南

```bash
# 1. 运行 Android JVM 单元测试（包含行为调度、网关协议、TTS 缓播与场景策略测试）
./gradlew testDebugUnitTest

# 2. 构建 Debug APK
./gradlew assembleDebug

# 3. 运行模拟器 UI 与行为捕获测试
./gradlew connectedDebugAndroidTest
```

最新打包产物可直接从 [GitHub Releases (v1.3.0)](https://github.com/lianbing22/EVA-X_Android_V1/releases/tag/v1.3.0) 下载安装。
