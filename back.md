# VoiceSummary 프로젝트 개발 기록

## 프로젝트 개요
- 패키지: `kr.jm.voicesummary`
- 목표: 온디바이스 AI 녹음기 앱 (완전 오프라인 STT + 요약)
- 핵심 가치: "서버 전송 0바이트" - 비행기 모드에서도 동작
- 타겟 기기: Nothing Phone 3a (Snapdragon 7s Gen 3)
- 기술 스택: Android Native (Kotlin) + Jetpack Compose

## 개발 로드맵

1. ✅ 녹음 기능 - 완료
2. ✅ STT 연동 - 완료 (Sherpa-ONNX + Whisper)
3. ✅ Room DB + UI - 완료
4. ✅ Clean Architecture + MVVM 리팩토링 - 완료
5. ✅ Foreground Service (백그라운드 처리) - 완료
6. ✅ 모델 선택 기능 - 완료
7. ⏳ MediaPipe LLM 요약 - 코드 완료, 모델 파일 필요

---

## 완료된 작업

### 1단계: 녹음 기능

- WAV 포맷 (16kHz, Mono, 16bit)
- 저장 경로: `/Android/data/kr.jm.voicesummary/files/`

### 2단계: STT 연동 (Sherpa-ONNX)

STT 솔루션 변경 히스토리:
- Whisper.cpp (CPU) → 너무 느림 (5초 음성 → 86초 처리)
- Qualcomm AI Hub (NPU) → Snapdragon 7s Gen 3 미지원
- Vosk → 빠르지만 한국어 small 모델만 존재 (정확도 낮음)
- **최종: Sherpa-ONNX + Whisper 모델**

현재 구성:
- 엔진: Sherpa-ONNX (k2-fsa 프로젝트)
- AAR: `app/libs/sherpa-onnx-1.12.20.aar`
- Provider: NNAPI (GPU/DSP/NPU 가속)
- 모델: Whisper (tiny/base/small/medium 선택 가능)

모델 선택 기능:
- Whisper Tiny (~40MB) - 가장 빠름, 정확도 낮음
- Whisper Base (~75MB) - 빠름, 정확도 중간
- Whisper Small (~250MB) - 보통, 정확도 높음
- Whisper Medium (~750MB) - 느림, 정확도 매우 높음
- 모델별 별도 폴더 저장 (여러 모델 동시 보관 가능)
- 다운로드된 모델은 ✓ 표시

### 3단계: Room DB + Expandable UI

- Recording 엔티티 (filePath, fileName, createdAt, fileSize, transcription, summary)
- 녹음 목록에서 펼치기/닫기로 STT 결과 표시
- Long click으로 삭제 다이얼로그
- 텍스트 선택 가능 + 복사 아이콘

### 4단계: Clean Architecture + MVVM 리팩토링

폴더 구조:
- `domain/` - model, repository interface
- `data/` - local (Room DB), repository impl
- `presentation/` - recording, list (Screen + ViewModel + UiState)
- `core/` - stt (Sherpa), llm (MediaPipe), audio
- `service/` - RecordingService (Foreground Service)

수동 DI:
- VoiceSummaryApp에서 의존성 생성
- ViewModel Factory 패턴으로 주입

### 5단계: Foreground Service

RecordingService 구현:
- 녹음, STT, 요약 모두 백그라운드에서 처리
- 알림바 상태 표시
- 화면 꺼져도 작업 계속됨

### 6단계: 모델 선택 UI

- 상단 배너에 현재 선택된 모델 표시
- "변경" 버튼으로 모델 선택 다이얼로그
- 다운로드 버튼으로 선택한 모델 다운로드
- 다운로드 진행률 + 압축 해제 상태 표시
- tar.bz2 압축 해제 (Apache Commons Compress)

---

## 주요 파일

### Core
- `SherpaTranscriber.kt` - STT 처리
- `SherpaModelDownloader.kt` - 모델 다운로드/관리
- `SttModel.kt` - 모델 enum (URL, 파일명 등)
- `LlmSummarizer.kt` - LLM 요약

### Presentation
- `RecordingListScreen.kt` - 녹음 목록 + 모델 배너
- `RecordingListViewModel.kt` - 상태 관리
- `RecordingScreen.kt` - 녹음 화면

### Service
- `RecordingService.kt` - Foreground Service

---

## 모델 파일 위치

- Sherpa 모델: `filesDir/sherpa-models/{MODEL_NAME}/`
  - 예: `sherpa-models/WHISPER_BASE/base-encoder.int8.onnx`
- Gemma LLM: `filesDir/models/gemma-2b-it-gpu-int4.bin` (추가 필요)

---

## 남은 작업

1. LLM 모델 파일 다운로드 및 테스트
   - Kaggle에서 gemma-2b-it-gpu-int4 다운로드
   - 다운로드 기능 구현 필요

2. STT 정확도 개선
   - small/medium 모델 테스트
   - 속도 vs 정확도 트레이드오프 확인

---

## 의존성

```kotlin
// Sherpa-ONNX (AAR 직접 포함)
implementation(files("libs/sherpa-onnx-1.12.20.aar"))

// tar.bz2 압축 해제
implementation("org.apache.commons:commons-compress:1.26.0")

// MediaPipe LLM
implementation("com.google.mediapipe:tasks-genai:0.10.14")
```

---

## 해결한 이슈

### Whisper 성능 문제
- CPU 기반 너무 느림 → Sherpa-ONNX + NNAPI로 해결

### Qualcomm NPU 미지원
- Snapdragon 7s Gen 3은 AI Hub 공식 지원 목록에 없음
- NNAPI로 GPU/DSP 가속 사용

### Vosk 정확도 문제
- 한국어 small 모델만 존재 → Sherpa + Whisper로 변경

### 모델 경로 크래시
- 절대 경로 사용 시 assetManager를 null로 설정해야 함
