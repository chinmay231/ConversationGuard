# ConversationGuard – V1 (Whisper.cpp + JNI)
ANDROID Version

Real-time voice monitoring for aggression, toxicity, and offensive speech using Whisper.cpp and Kotlin.


---

## 🎯 Features
- 🧠 On-device STT via Whisper.cpp (C++ + JNI)
- 🎙️ Real-time transcription from microphone
- 🚦 Aggression & Toxicity scoring → Light indicator (Red/Yellow/Green)
- 📱 Jetpack Compose UI + AppState
- 💾 SQLite-based session summary storage

---

## 🔧 Tech Stack

- Kotlin + Jetpack Compose
- Whisper.cpp via JNI
- CMake + NDK
- AudioRecord (16 kHz PCM)
- Heuristic-based toxicity detection
- Minimum SDK: 26
- Add words toxic score adders in the list in Toxicity Heuristics
---

## 🗂️ Structure Overview

| Module                  | Purpose                            |
|-------------------------|-------------------------------------|
| `MainActivity.kt`       | Audio pipeline & app control       |
| `WhisperBridge.kt`      | JNI bridge to whisper.cpp          |
| `whisper_jni.cpp`       | JNI glue logic (C++)               |
| `whisper.cpp/`          | Whisper C++ engine                 |
| `ToxicityHeuristics.kt` | Text & prosody-based scoring        |
| `ConversationGuardScreen.kt` | Compose UI view            |

---

## 📦 Assets

- `assets/models/ggml-tiny.en.bin` – Whisper.cpp model file

---

## 📸 Screenshot
<img width="692" height="1467" alt="image" src="https://github.com/user-attachments/assets/e6f60cc7-a3b9-4c35-8e31-3bc96305291e" />
---

## 🚀 Getting Started

```bash
git clone https://github.com/chinmay231/ConversationGuard-v1.git
cd ConversationGuard-v1
# Open in Android Studio and run on device with mic permission
