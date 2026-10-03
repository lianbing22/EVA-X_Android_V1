# EVA-X Android V1 Design Specification

**Date:** 2026-10-01  
**Status:** Draft for review  
**Decision recorded:** Native Android app; demonstration-first MVP; Kotlin + Jetpack Compose.

## 1. Product goal

Build a native Android demonstration app for EVA-X, a voice-and-text AI desk companion. The first version should make the interaction feel tangible on a phone: the assistant listens, thinks, shows simulated work, and speaks a result. It should also establish a clean seam for connecting an EVA Gateway and real agent providers later.

This is a demonstrable client, not a real enterprise assistant yet. All answers and task progress in V1 are clearly marked as sample/demo content.

## 2. Target user and success criteria

The target user is a presenter demonstrating the EVA-X concept on an Android phone.

V1 succeeds when the user can:

1. Open the app and see an animated EVA-X assistant in an idle state.
2. Enter a request by typing or speaking Chinese.
3. See distinct listening, thinking, executing, and completed states.
4. Try at least two guided office scenarios and receive a coherent demo result.
5. Hear the result through Android text-to-speech and stop playback.
6. Use the app without model credentials, account setup, or network configuration.

The deliverable is an Android Studio project that can produce a debug APK. The current coding environment has Java 17 but no Android SDK, adb, or Gradle installed, so local APK compilation may not be available.

## 3. V1 user experience

### Home / conversation

- A single-screen conversational interface branded EVA-X.
- A native Compose-drawn avatar with subtle animation; no external image assets required.
- The avatar and status label reflect the current assistant state:
  - Idle: greeting and ready indicator.
  - Listening: microphone/listening indicator and animated face.
  - Thinking: short processing animation.
  - Executing: visible simulated task steps with progress.
  - Completed: result card and completion state.
  - Error: short explanation and a retry action.
- A scrollable conversation area for the current app session.
- Text composer with send action, microphone action, and a speech playback control.
- Two example prompt chips: “查今天的安排” and “整理会议纪要”.
- A visible “演示模式” label so sample data cannot be mistaken for live company data.

### Demonstration scenarios

1. **Office schedule question**  
   Prompt: “今天下午我有什么事情？”  
   Demo answer: “下午 3 点有客户需求讨论，5 点有项目复盘。” The response is labeled as sample schedule data.

2. **Meeting summary task**  
   Prompt: “帮我整理刚才的会议纪要。”  
   The app shows simulated steps such as “整理会议内容”, “提取待办”, and “生成摘要”, then displays a sample summary and three sample follow-up items. It does not send anything to a person or system.

Other prompts receive a concise demo fallback that explains the current demo can show office Q&A and meeting-summary flows.

### Voice interaction

- Use Android speech recognition from within the app after the user grants microphone permission.
- Recognized text is inserted into the same conversation flow as typed input.
- Use Android TextToSpeech for spoken replies, with a visible stop control.
- If recognition is unavailable, permission is denied, or no speech is recognized, show a recoverable in-app message and keep text entry available.
- Recognition availability and behavior may depend on the Android device’s installed speech service. The app does not upload audio to an EVA-X server in V1.

## 4. Architecture

Keep the first version small while preserving a provider boundary:

- **Presentation:** Compose screen, visual state, and conversation interactions.
- **State / domain:** ViewModel and an AssistantEngine interface that accepts a user message and emits demo progress plus a response.
- **Demo provider:** DemoAssistantEngine, with deterministic schedule, meeting-summary, and fallback scenarios.
- **Android voice adapters:** SpeechRecognizer and TextToSpeech wrappers, isolated from UI state.
- **Future connection seam:** a documented GatewayAssistantEngine interface implementation point, not implemented in V1.

The app must not contain provider API keys or call WorkBuddy, OpenAI, Qwen, or other remote model APIs in this version.

## 5. Data and privacy

- No login, analytics, remote storage, or backend connection.
- Conversation content is held in memory for the current app session and is cleared when the process ends.
- Fixed demo data is visibly identified as sample content.
- Microphone permission is requested only when the user taps the microphone button.
- TTS is local Android system functionality.
- The app does not request camera, location, contacts, Bluetooth, or notification permissions.

## 6. Error and interruption behavior

- Denied microphone permission: explain how to continue with typing; do not repeatedly prompt.
- Speech service missing or recognition error: return to idle and preserve typed input.
- Empty recognition result: show “没有听清，再试一次” with retry.
- Empty typed message: do not submit.
- Repeated send while a demo run is active: disable the send action until completion.
- TTS interrupted or stopped: return playback controls to idle without losing the response.
- Demo run canceled by leaving or restarting the app: no external action has occurred.

## 7. Out of scope

- Real OpenAI/ChatGPT, WorkBuddy, Qwen, DeepSeek, or enterprise Agent integration.
- Gateway server, provider routing, MCP Hub, API credential management, and audit logging.
- Physical microphone arrays, camera-based person tracking, gimbal/servo control, RGB hardware, or ESP32 communication.
- Persistent conversation memory, identity, login, cloud sync, or enterprise data access.
- Background wake-word detection and always-on listening.
- Sending messages, creating calendar events, or performing actions in external systems.

## 8. Verification expectations

- Unit tests for prompt routing and deterministic demo responses.
- Unit tests for state transitions through listening, processing, execution, and completion or error.
- UI-level verification that prompt chips submit a request and show the response, plus microphone permission/error fallback where the available Android test environment permits.
- Build verification with Android Gradle tooling when an Android SDK is available.
