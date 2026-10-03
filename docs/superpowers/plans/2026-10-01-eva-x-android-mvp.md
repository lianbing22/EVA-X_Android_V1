# EVA-X Android V1 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Track steps with checkboxes.

**Goal:** Build an Android Studio project for a demonstrable EVA-X companion app with Chinese text and speech interaction, animated state feedback, and deterministic sample tasks.

**Architecture:** Kotlin + Jetpack Compose for the screen, a ConversationViewModel for session state, and a Flow-based AssistantEngine boundary for the local demo provider. Android SpeechRecognizer and TextToSpeech are isolated behind adapters so a later Gateway integration can replace the demo provider without changing the screen.

**Tech Stack:** Android Gradle Plugin 9.2.0, Gradle 9.4.1, JDK 17, Kotlin Gradle Plugin 2.2.10, Compose BOM 2026.09.00, Activity Compose 1.13.0, Lifecycle ViewModel Compose 2.11.0, kotlinx.coroutines 1.11.0. Use minSdk 26, compileSdk 36, and targetSdk 36. The app makes no direct network requests; Android's installed speech recognition service may apply its own network behavior. Versions were checked against official Android and Kotlin release documentation on 2026-10-01.

**Spec:** docs/superpowers/specs/2026-10-01-eva-x-android-design.md

## Global Constraints

- Build a native Android app with Kotlin and Jetpack Compose.
- All answers and task progress are sample/demo content, visibly marked with “演示模式” or “演示数据”.
- No provider API keys; no remote model, WorkBuddy, Gateway, MCP, login, analytics, remote storage, or backend calls.
- Keep conversation content in memory for the current app session and clear it when the process ends.
- Request microphone permission only after the user taps the microphone control.
- Do not request camera, location, contacts, Bluetooth, or notification permissions.
- Do not send messages, create calendar events, or perform actions in external systems.
- Disclose before first recording that Android’s installed speech recognition service handles recognition and may apply its own audio processing/transmission behavior.

## Review Focus

1. **Blank or whitespace-only input:** ignore it and preserve the current conversation; tested in Task 2.
2. **Second submission while a demo run is active:** do not add a duplicate request or start a second engine collection; tested in Task 2.
3. **Empty recognition result:** show “没有听清，再试一次”, return to a usable input state, and keep typed text intact; tested in Task 2.
4. **Microphone permission denied or speech service unavailable:** keep text entry usable and explain the recovery path; tested in Tasks 2 and 4.
5. **TTS not initialized or stopped mid-utterance:** keep the response visible and restore the playback control; tested in Task 3 and manually verified in Task 5 when an Android device is available.

## Project File Map

- Gradle root and app configuration: settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/gradle-wrapper.properties, gradle/wrapper/gradle-wrapper.jar, gradlew, gradlew.bat, app/build.gradle.kts.
- App entry and Android permissions: app/src/main/AndroidManifest.xml and app/src/main/java/com/evax/mobile/MainActivity.kt.
- Domain: app/src/main/java/com/evax/mobile/domain/AssistantEngine.kt, AssistantEvent.kt, AssistantResult.kt, SpeechInputFailure.kt, and DemoAssistantEngine.kt.
- Conversation state: app/src/main/java/com/evax/mobile/presentation/ConversationViewModel.kt and ConversationUiState.kt.
- Compose UI: app/src/main/java/com/evax/mobile/ui/ConversationScreen.kt, AssistantAvatar.kt, and theme/EvaXTheme.kt.
- Speech adapters: app/src/main/java/com/evax/mobile/platform/voice/SpeechInputController.kt, AndroidSpeechInputController.kt, SpeechOutputController.kt, AndroidTextToSpeechController.kt, and SpeechErrorMapper.kt.
- Tests: app/src/test/java/com/evax/mobile/domain/DemoAssistantEngineTest.kt, app/src/test/java/com/evax/mobile/presentation/ConversationViewModelTest.kt, app/src/test/java/com/evax/mobile/platform/voice/SpeechErrorMapperTest.kt, app/src/androidTest/java/com/evax/mobile/ui/ConversationScreenTest.kt, app/src/androidTest/java/com/evax/mobile/platform/voice/SpeechPermissionDisclosureTest.kt, and app/src/androidTest/java/com/evax/mobile/EvaXEndToEndTest.kt.
- User setup notes: README.md.

## Task 1: Android project baseline and deterministic demo engine

**Files:**
- Create the Gradle root and app files listed in Project File Map.
- Create MainActivity.kt as a minimal app entry using the EVA-X theme and a temporary placeholder surface; full wiring is in Task 5.
- Create domain files AssistantEngine.kt, AssistantEvent.kt, AssistantResult.kt, SpeechInputFailure.kt, and DemoAssistantEngine.kt.
- Test: app/src/test/java/com/evax/mobile/domain/DemoAssistantEngineTest.kt.

**Interfaces:**
- Produces AssistantEngine.respond(prompt: String): Flow<AssistantEvent>.
- Produces AssistantEvent.Progress(step: String, index: Int, total: Int) and AssistantEvent.Completed(result: AssistantResult).
- Produces AssistantResult(text: String, sampleLabel: String, followUps: List<String>).
- Produces SpeechInputFailure values PERMISSION_DENIED, SERVICE_UNAVAILABLE, NO_MATCH, NETWORK, and UNKNOWN for use by later tasks.
- DemoAssistantEngine emits deterministic schedule, meeting-summary, and fallback responses.

- [ ] Step 1: Create the Android Gradle project baseline and standard Gradle Wrapper with package namespace com.evax.mobile, app label EVA-X, minSdk 26, compileSdk 36, targetSdk 36, and the pinned plugin/dependency versions in Tech Stack.
- [ ] Step 2: Write schedulePromptEmitsLabeledSampleSchedule, asserting the response includes “下午 3 点有客户需求讨论，5 点有项目复盘” and sampleLabel equals “演示数据”.
- [ ] Step 3: Write meetingPromptEmitsThreeStepsAndThreeFollowUps, asserting the steps equal “整理会议内容”, “提取待办”, “生成摘要” in order and the result has three follow-ups.
- [ ] Step 4: Write unsupportedPromptEmitsDemoFallback, asserting the fallback states that V1 supports office Q&A and meeting-summary demonstration only.
- [ ] Step 5: Write meetingIntentTakesPriorityOverScheduleWords, asserting “今天下午的会议纪要” emits meeting steps rather than a schedule response.
- [ ] Step 6: Run ./gradlew :app:testDebugUnitTest --tests com.evax.mobile.domain.DemoAssistantEngineTest and confirm the tests fail because DemoAssistantEngine is not implemented.
- [ ] Step 7: Implement DemoAssistantEngine so meeting prompts containing “会议” or “纪要” take priority and emit the three ordered steps, schedule prompts containing “安排” or “下午” return the fixed schedule, and all other prompts return the fixed demo fallback. Progress indexes are 1-based; use a short delay between meeting steps so the UI can show execution.
- [ ] Step 8: Run ./gradlew :app:testDebugUnitTest --tests com.evax.mobile.domain.DemoAssistantEngineTest and confirm all four tests pass.
- [ ] Step 9: Commit the project baseline and demo engine.

## Task 2: Conversation state machine and interaction tests

**Files:**
- Create ConversationUiState.kt and ConversationViewModel.kt.
- Test: app/src/test/java/com/evax/mobile/presentation/ConversationViewModelTest.kt.

**Interfaces:**
- Consumes AssistantEngine.respond(prompt: String): Flow<AssistantEvent>.
- Produces AssistantPhase values IDLE, LISTENING, THINKING, EXECUTING, COMPLETED, and ERROR.
- Produces ConversationMessage(id: Long, role: MessageRole, text: String, isSample: Boolean, followUps: List<String>).
- Produces VoicePlaybackState(isReady: Boolean, isSpeaking: Boolean).
- Produces ConversationUiState(draft: String, messages: List<ConversationMessage>, phase: AssistantPhase, currentStep: String?, progressSteps: List<String>, notice: String?, isProcessing: Boolean, voicePlayback: VoicePlaybackState).
- Produces ConversationViewModel.uiState: StateFlow<ConversationUiState>.
- Consumes domain SpeechInputFailure values.
- Produces onDraftChanged(text: String), submitPrompt(text: String? = null), onListeningStarted(), onSpeechResult(text: String), onSpeechFailure(failure: SpeechInputFailure), onVoicePlaybackChanged(state: VoicePlaybackState), and clearNotice().

- [ ] Step 1: Write submitPromptTrimsAndCompletes, asserting a trimmed user message and a completed sample assistant message appear in order.
- [ ] Step 2: Write submitPromptIgnoresBlank, asserting blank and whitespace-only text add no message and leave phase IDLE.
- [ ] Step 3: Write submitPromptIgnoresWhileBusy, asserting a second prompt does not add a message or collect the engine twice.
- [ ] Step 4: Write emptySpeechResultShowsRetryNotice, asserting the notice is “没有听清，再试一次” and text composition remains available.
- [ ] Step 5: Write permissionDeniedKeepsComposerUsable, asserting a helpful notice appears and a previously typed draft is preserved.
- [ ] Step 6: Write serviceUnavailableKeepsComposerUsable, asserting speech service failure returns to a usable text-entry state.
- [ ] Step 7: Write recognizedSpeechSubmitsMessage, asserting a nonblank recognized phrase enters the same submit flow as typed text.
- [ ] Step 8: Write listeningCallbackSetsListeningPhase, asserting onListeningStarted sets phase to LISTENING.
- [ ] Step 9: Write voicePlaybackStateUpdatesWithoutChangingMessages, asserting a TTS state update leaves conversation messages unchanged.
- [ ] Step 10: Write clearNoticeRemovesNotice, asserting clearNotice removes only the current notice.
- [ ] Step 11: Write progressEventsUpdateVisibleSteps, asserting engine progress events appear in order in progressSteps.
- [ ] Step 12: Write engineFailureKeepsRequestAndShowsError, asserting the user message remains visible, isProcessing becomes false, and phase becomes ERROR.
- [ ] Step 13: Run ./gradlew :app:testDebugUnitTest --tests com.evax.mobile.presentation.ConversationViewModelTest and confirm failures are caused by missing state-machine behavior.
- [ ] Step 14: Implement ConversationViewModel with a StateFlow state, active-run guard, progressSteps updates, speech error recovery, and playback-state updates. Map engine exceptions to ERROR while retaining the user’s request.
- [ ] Step 15: Run ./gradlew :app:testDebugUnitTest --tests com.evax.mobile.presentation.ConversationViewModelTest and confirm all twelve tests pass.
- [ ] Step 16: Commit the state model and tests.

## Task 3: Compose conversation screen and animated avatar

**Files:**
- Create ConversationScreen.kt, AssistantAvatar.kt, and theme/EvaXTheme.kt.
- Test: app/src/androidTest/java/com/evax/mobile/ui/ConversationScreenTest.kt.

**Interfaces:**
- Consumes ConversationUiState and the callbacks produced by ConversationViewModel.
- ConversationScreen(state, onDraftChanged, onSubmit, onMicTap, onSpeakLatest, onStopSpeaking).
- AssistantAvatar(phase: AssistantPhase).

- [ ] Step 1: Write exampleChipsSubmitExpectedPrompts, asserting the two chips send “查今天的安排” and “整理会议纪要”.
- [ ] Step 2: Write screenShowsDemoModeAndSampleLabels, asserting the “演示模式” badge and response “演示数据” label appear.
- [ ] Step 3: Write avatarShowsAccessibleStateLabel, asserting listening and executing phases expose matching Chinese status text and content descriptions.
- [ ] Step 4: Write playbackControlsReflectReadyAndSpeakingState, asserting playback is disabled before TTS initialization, changes to Stop while speaking, and does not remove the response.
- [ ] Step 5: Run ./gradlew :app:connectedDebugAndroidTest and confirm the Compose UI tests fail because the screen and avatar have not been implemented.
- [ ] Step 6: Implement a single-screen dark charcoal UI with cyan/lilac accents, central Compose-drawn animated face, status label, scrollable conversation, example chips, sample result cards, and persistent composer controls.
- [ ] Step 7: Keep microphone, speak, and stop controls accessible by visible labels and semantics; disable send while isProcessing is true.
- [ ] Step 8: Run ./gradlew :app:connectedDebugAndroidTest and confirm all four UI behaviors pass.
- [ ] Step 9: Commit the Compose screen and tests.

## Task 4: Android speech input and text-to-speech adapters

**Files:**
- Create SpeechInputController.kt, AndroidSpeechInputController.kt, SpeechOutputController.kt, AndroidTextToSpeechController.kt, and SpeechErrorMapper.kt.
- Modify AndroidManifest.xml and ConversationScreen.kt for the first-use voice disclosure.
- Test: app/src/test/java/com/evax/mobile/platform/voice/SpeechErrorMapperTest.kt.
- Test: app/src/androidTest/java/com/evax/mobile/platform/voice/SpeechPermissionDisclosureTest.kt.

**Interfaces:**
- SpeechInputController.start(onResult: (String) -> Unit, onFailure: (SpeechInputFailure) -> Unit), cancel(), and destroy().
- SpeechOutputController.state: StateFlow<VoicePlaybackState>, speak(text: String), stop(), and shutdown(). VoicePlaybackState is declared in ConversationUiState.kt.
- SpeechInputFailure values include PERMISSION_DENIED, SERVICE_UNAVAILABLE, NO_MATCH, NETWORK, and UNKNOWN.
- SpeechErrorMapper maps Android SpeechRecognizer errors to SpeechInputFailure.

- [ ] Step 1: Write mapsNoMatchToRetryableFailure, asserting Android no-match maps to SpeechInputFailure.NO_MATCH.
- [ ] Step 2: Write mapsUnavailableOrNetworkFailure, asserting unavailable service maps to SERVICE_UNAVAILABLE and network errors map to NETWORK.
- [ ] Step 3: Run ./gradlew :app:testDebugUnitTest --tests com.evax.mobile.platform.voice.SpeechErrorMapperTest and confirm it fails because SpeechErrorMapper is missing.
- [ ] Step 4: Implement SpeechErrorMapper and run the same test command to green.
- [ ] Step 5: Write micTapShowsSpeechServiceDisclosureBeforePermissionRequest in SpeechPermissionDisclosureTest.
- [ ] Step 6: Run ./gradlew :app:connectedDebugAndroidTest and confirm the disclosure test fails because the first-use notice is missing.
- [ ] Step 7: Implement the first-use disclosure in ConversationScreen, explaining that the installed Android speech service handles recognition and may process audio according to its own behavior.
- [ ] Step 8: Add RECORD_AUDIO only; add manifest queries for android.speech.RecognitionService and android.intent.action.TTS_SERVICE; do not add INTERNET or other permissions. Android documents the RECORD_AUDIO requirement, main-thread and listener ordering, destroy-on-release rule, and Android 11+ RecognitionService query.
- [ ] Step 9: Implement AndroidSpeechInputController on the main thread, register its listener before startListening, handle unavailable service/errors, cancel listening, and destroy the recognizer when released.
- [ ] Step 10: Implement AndroidTextToSpeechController with asynchronous initialization, zh-CN language selection, utterance completion state, stop(), and shutdown().
- [ ] Step 11: Run ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest and confirm the unit and UI suites pass.
- [ ] Step 12: Commit speech adapters, manifest changes, and verification notes.

## Task 5: End-to-end integration and handoff package

**Files:**
- Modify MainActivity.kt, AndroidManifest.xml, ConversationScreen.kt, and app/build.gradle.kts as needed.
- Create README.md and a simple vector launcher icon under app/src/main/res/drawable/.
- Test: app/src/androidTest/java/com/evax/mobile/EvaXEndToEndTest.kt.

**Interfaces:**
- Connects ConversationViewModel, DemoAssistantEngine, AndroidSpeechInputController, AndroidTextToSpeechController, and ConversationScreen.
- Keeps the default provider as DemoAssistantEngine; no API keys or remote provider URLs exist in the project.

- [ ] Step 1: Write scheduleExampleChipRendersSampleAnswer in EvaXEndToEndTest, asserting the schedule response card and “演示数据” label appear.
- [ ] Step 2: Write meetingPromptRendersStepsAndFollowUps in EvaXEndToEndTest, asserting the three progress steps and three follow-up items appear.
- [ ] Step 3: Run ./gradlew :app:connectedDebugAndroidTest and confirm both end-to-end tests fail before the full app wiring is implemented.
- [ ] Step 4: Replace the placeholder MainActivity content and wire DemoAssistantEngine, ConversationViewModel, and ConversationScreen.
- [ ] Step 5: Connect the speech input adapter and request microphone permission only after the disclosure is accepted; on denial, call onSpeechFailure(PERMISSION_DENIED).
- [ ] Step 6: Collect VoicePlaybackState and release speech controllers with the Activity lifecycle.
- [ ] Step 7: Manually verify permission grant/denial, recognized Chinese text, speech-service unavailable state, TTS playback, and stopping playback on an Android device when available.
- [ ] Step 8: Write README setup steps, minimum Android API 26, microphone disclosure, demo-data limits, Android Studio import instructions, and debug APK location.
- [ ] Step 9: Run ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug and record the test/build result.
- [ ] Step 10: Inspect the merged manifest to confirm INTERNET and unrequested permissions are absent.
- [ ] Step 11: If the Android SDK remains unavailable in the coding environment, record that tests/build were not run here; do not report an APK as built or verified.
- [ ] Step 12: Package the complete Android Studio project source as EVA-X_Android_V1_Source.zip and commit the final integration.

## Implementation References

- Android Gradle Plugin 9.2.0 release notes: https://developer.android.com/build/releases/agp-9-2-0-release-notes
- Compose BOM setup and version guidance: https://developer.android.com/develop/ui/compose/bom
- AndroidX stable release versions: https://developer.android.com/jetpack/androidx/versions/stable-channel
- Kotlin coroutines releases: https://github.com/Kotlin/kotlinx.coroutines/releases
- SpeechRecognizer API and Android 11+ manifest query: https://developer.android.com/reference/android/speech/SpeechRecognizer
- TextToSpeech API, initialization, stop, and service query: https://developer.android.com/reference/android/speech/tts/TextToSpeech

## Self-Review

- Spec coverage: UI state/animation is Task 3; schedule and meeting flows are Tasks 1, 2, and 5; voice, permissions, and TTS are Task 4; data boundaries and handoff notes are Tasks 4 and 5; excluded real providers and hardware remain excluded.
- Interface consistency: Task 1 emits AssistantEvent.Progress and Completed and defines SpeechInputFailure; Task 2 collects those event types and updates progressSteps and VoicePlaybackState; Task 3 consumes ConversationUiState and the ViewModel callbacks; Task 4 adapters publish VoicePlaybackState through the interface declared in Task 4.
- Review Focus coverage: all five listed input/failure classes have named tests or an explicit device verification step.
- Scope: one Android client project with a local demo engine; no independent backend subsystem is included.
- Environment limit: the workspace currently has Java 17 but no Android SDK, adb, or Gradle, so build and device checks require an Android development environment.
