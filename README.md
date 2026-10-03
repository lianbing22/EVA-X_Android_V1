# EVA-X Android V1

EVA-X V1 is a native Android demonstration app built with Kotlin and Jetpack Compose. It presents a voice-and-text desk companion interaction with a local, deterministic demo engine.

## What works in the demo

- Chinese text prompts and two guided office scenarios.
- Animated assistant face with listening, thinking, execution, completion, and error states.
- Meeting-summary progress steps and sample follow-up items.
- Android speech recognition after a first-use disclosure and microphone permission grant.
- Android TextToSpeech playback with a stop control.
- All answers and progress are sample content. No WorkBuddy, OpenAI, Qwen, Gateway, MCP, or other remote provider is connected.

The demo schedule is fixed: “下午 3 点有客户需求讨论，5 点有项目复盘。” Conversation data stays in memory for the current app process. The app does not request camera, location, contacts, Bluetooth, or notification permissions, and its manifest does not request internet access.

## Open and run

1. Install Android Studio with JDK 17, Android SDK Platform 37, and Android Build Tools 37. `compileSdk` is 37 because the pinned Compose dependencies require it; the app still targets API 36.
2. Install Gradle 9.4.1 locally, then run `gradle wrapper --gradle-version 9.4.1` from the project root to create the standard wrapper files.
3. Open this project folder in Android Studio and allow Gradle sync.
4. Run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator.

The project is pinned to Gradle 9.4.1, Android Gradle Plugin 9.2.0, Kotlin 2.2.10, and Compose BOM 2026.09.00. AGP 9 provides built-in Kotlin support; the Compose compiler plugin stays pinned to Kotlin 2.2.10. This source handoff contains the wrapper version properties but not `gradle-wrapper.jar` or launcher scripts because Gradle was not installed in the implementation environment. After the one-time wrapper generation above, use `./gradlew` (Windows: `gradlew.bat`).

Useful commands after wrapper generation:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` after a successful `assembleDebug` build.

## Voice and privacy

The app explains before recording that the installed Android speech service handles recognition and may process or transmit audio according to that service’s behavior. EVA-X V1 does not upload audio to an EVA-X server. Microphone permission is requested only after the user taps “语音输入” and accepts the disclosure. If permission is denied or speech recognition is unavailable, text entry remains available.

Text-to-speech uses the device’s Android TTS service and requests the `zh-CN` voice. Voice availability depends on installed Android services and language data.

## Project structure

- `domain/`: demo engine, event stream, result models, and speech failure types.
- `presentation/`: in-memory conversation state and state machine.
- `ui/`: Compose screen, animated avatar, and EVA-X theme.
- `platform/voice/`: Android speech recognition and TextToSpeech adapters.

The `AssistantEngine` interface is the replacement seam for a future EVA Gateway integration. No remote provider adapter or credentials are included in this version.
