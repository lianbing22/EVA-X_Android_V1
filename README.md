# EVA-X Android — WorkBuddy Companion 1.3

Native Android desk companion built with Kotlin and Jetpack Compose.

## Current interface

- Pure black immersive home with large, minimal teal eyes; no iris or face frame.
- Random idle gaze and blinking, tap-to-look, double-tap wink, and separate listening, thinking, execution, completion and error expressions.
- Voice and tools are the main home controls. Swipe upward to open conversation details; shortcuts and settings live in drawers.
- A compact current-task card shows reported progress. Replies appear briefly as subtitles and remain in conversation details.
- Companion mode allows automatic speech. Do Not Disturb pauses idle gestures and automatic speech while allowing deliberate interaction. Rest stops speech recognition, playback, camera tracking and keep-screen-on; tap to wake.
- Camera tracking is opt-in from settings. Audio levels come from the recognition service instead of a synthetic waveform. Reduced motion is available in settings.
- Local idle stories: web surfing, reading, catching stars, coffee, gaming, stretching, napping and watching rain. Settings offers individual previews and an automatic-play switch. They never open browsers, read the computer or call a model.
- Listening distinguishes microphone preparation, readiness, speech and recognition. Result reactions distinguish reply availability, confirmed computer completion, uncertainty, failure and attention requests. Task cards keep the eye layout anchored.
- Stories yield to input, tasks, queued/active speech, unresolved notices, open drawers and backgrounding. DND, Rest, reduced motion and low battery/power saver suppress them. Story and reduced-motion preferences persist across app restarts.

## Computer connection and demo data

The settings drawer now has a computer connection panel with a configurable bridge URL, masked pairing code, save and connection test. Pairing settings are encrypted with Android Keystore. Testing unsaved input does not save it; changed input invalidates the earlier validation.

`GatewayAssistantEngine` only accepts the paired `evax-workbuddy-v1` bridge and verifies the official local assistant's authorization and online state before sending a command. The matching computer service is in `../evax-workbuddy-bridge`; its README explains WorkBuddy app registration, OAuth, USB/LAN setup and real task acceptance. Default port is 3099. The old 3088 service was model chat/DSH bookkeeping and is not used.

Normal operation never falls back to demo data. Missing configuration, pairing failures, missing OAuth, offline desktop assistant and failed streams produce actionable errors. The existing debug intent and instrumentation still explicitly select isolated local demo engines for UI QA.

The public local-assistant API returns messages without a reliable task-end or remote-cancellation signal. A returned reply is labeled as a WorkBuddy reply, not independently verified task completion. A successful computer-task reaction requires a matched request/run ID and structured terminal evidence from the local run stream. Model output text and stream EOF are insufficient. “停止接收” closes the phone connection; an already submitted computer task may continue. Ambiguous submission failures offer a computer-state check rather than one-tap retry.

Calendar, meeting material and screenshot capabilities depend on actual WorkBuddy connectors, files, skills and permissions. Shortcuts submit instructions; they do not imply that those capabilities are already configured.

Conversation history is in memory. Process termination clears it; orientation changes preserve the current interface state.

## Build and run

Requires JDK 17, Gradle 9.4.1, Android SDK Platform 37 and corresponding Build Tools. Minimum device API is 26; target API is 36. The project pins AGP 9.2.0, Kotlin 2.2.10 and Compose BOM 2026.09.00. AGP 9 supplies built-in Kotlin support.

This source contains wrapper version properties but no wrapper launcher or JAR. Use an existing Gradle 9.4.1 installation:

```sh
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For isolated local UI QA, a debug build can explicitly use the demo engine:

```sh
adb shell am start -n com.evax.mobile/.MainActivity --ez demo_mode true
```

The intent option is ignored by non-debug builds. Instrumentation tests also use local demo or loopback fake servers, and do not trigger computer actions.

## Voice and camera

The installed Android speech service handles recognition and may process or transmit audio. Microphone permission is requested after the user taps voice input and accepts the disclosure. Recognized text is sent to the active assistant gateway when one is available. If no speech service is available, the interface offers text entry and stops showing a recording state.

Text-to-speech uses the installed Android TTS service and requests a Chinese voice. Actual speech recognition, language data, playback and face tracking depend on the device and require physical-device verification.

Playback animation starts with the actual TTS utterance callback; enqueued speech also suppresses idle stories. Listening callbacks carry a session ID, so cancelled or superseded recognition cannot submit an old command.

The manifest includes internet, microphone and camera permissions; camera permission is only requested when the user enables face tracking.

## Structure

- `domain/`: gateway/demo engines, event stream and result/source models.
- `presentation/`: conversation state, real progress, cancellation and completion timing.
- `ui/`: companion home, drawers, animated Canvas eyes and theme.
- `platform/voice/`: Android speech recognition and playback adapters.
- `platform/vision/`: camera tracking adapter.
