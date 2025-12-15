# VoiceSummary 프로젝트 개발 기록

## 프로젝트 개요
- 패키지: `kr.jm.voicesummary`
- 목표: 온디바이스 AI 녹음기 앱 (오프라인 STT + 요약)
- 핵심 가치: "서버 전송 0바이트" - 보안/프라이버시 강조
- 타겟 기기: Nothing Phone 3a (Snapdragon 7s Gen 3)
- 기술 스택: Android Native (Kotlin) + Jetpack Compose

## 기술 선정 배경

### STT (음성→텍스트)
- 선택: **Whisper.cpp** (OpenAI Whisper의 C++ 포팅)
- 모델: Base 또는 Small (Tiny는 한국어 성능 부족)
- 이유: 오프라인 완벽 지원, 한국어 인식률 우수, 크로스플랫폼(iOS 확장 용이)

### LLM (텍스트 요약)
- 선택: **MediaPipe LLM Inference** + Gemma 2B (4bit 양자화)
- 이유: 구글 공식 도구, Android/iOS 모두 지원, NPU 가속 자동 처리

### 크로스플랫폼 전략
- Flutter 대신 **KMP (Kotlin Multiplatform)** 추천
- 이유: 백그라운드 녹음 안정성, AI 네이티브 연동 용이

## 개발 로드맵

1. ✅ 녹음 기능 - 완료
2. ⏳ Whisper STT 연동 - 진행 예정
3. ⬚ MediaPipe LLM 요약
4. ⬚ 백그라운드 서비스
5. ⬚ Room DB + UI 완성

## 완료된 작업

### 1단계: 녹음 기능 (완료)

**구현 파일:**
- `app/src/main/AndroidManifest.xml` - RECORD_AUDIO 권한 추가
- `app/src/main/java/kr/jm/voicesummary/audio/AudioRecorder.kt` - WAV 녹음 클래스
- `app/src/main/java/kr/jm/voicesummary/ui/RecordingScreen.kt` - 녹음 UI
- `app/src/main/java/kr/jm/voicesummary/MainActivity.kt` - 권한 처리 + 녹음 연결

**녹음 설정:**
- 포맷: WAV (PCM)
- 샘플레이트: 16kHz (Whisper 권장)
- 채널: Mono
- 비트: 16bit

**저장 경로:**
- `/storage/emulated/0/Android/data/kr.jm.voicesummary/files/`
- 파일명: `yyyyMMdd_HHmmss.wav`

## 다음 작업: 2단계 Whisper STT 연동

**선택지:**
- A. whisper.cpp 직접 통합 (NDK + CMake) - 성능 최고, 세팅 복잡
- B. 커뮤니티 래퍼 라이브러리 사용 - 세팅 쉬움

**필요한 것:**
- Whisper 모델 파일 (.bin) - ggml-base.bin 또는 ggml-small-q5_0.bin
- JNI 바인딩 또는 래퍼 라이브러리

## 참고 정보

### 온디바이스 AI 핵심 개념
- **NPU**: AI 연산 전용 저전력 칩. CPU/GPU보다 효율적
- **양자화(Quantization)**: 모델 용량 압축 기술. Float32 → Int8/Int4로 변환
- **Op(Operator)**: 연산 명령어. NPU가 지원 안 하면 CPU로 폴백되어 느려짐

### Qualcomm AI Hub
- 퀄컴이 Whisper, Gemma 등을 스냅드래곤 NPU에 최적화해둔 모델 저장소
- Nothing Phone 3a (Snapdragon 7s Gen 3)에서 NPU 가속 가능
- 단, 전용 SDK 쓰면 다른 칩셋(엑시노스, 미디어텍)에서 안 돌아감
- 범용성 위해 표준 라이브러리(TFLite, MediaPipe) 사용 권장

### 용어 정리
- **ONNX**: MS+Meta가 만든 AI 모델 범용 포맷 (어디서든 실행 가능)
- **TFLite/LiteRT**: 구글의 모바일 AI 런타임
- **whisper.cpp**: Whisper를 C++로 포팅한 경량 버전
- **MediaPipe**: 구글의 크로스플랫폼 AI 파이프라인 도구
