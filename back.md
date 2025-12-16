# VoiceSummary 프로젝트 개발 기록

## 프로젝트 개요
- 패키지: `kr.jm.voicesummary`
- 목표: 온디바이스 AI 녹음기 앱 (오프라인 STT + 요약)
- 핵심 가치: "서버 전송 0바이트" - 보안/프라이버시 강조
- 타겟 기기: 모든 안드로이드 폰
- 기술 스택: Android Native (Kotlin) + Jetpack Compose

## 개발 로드맵

1. ✅ 녹음 기능 - 완료
2. ✅ Whisper STT 연동 - 완료
3. ✅ Room DB + UI - 완료
4. ✅ Clean Architecture + MVVM 리팩토링 - 완료
5. ✅ Foreground Service (백그라운드 처리) - 완료
6. ⏳ MediaPipe LLM 요약 - 코드 완료, 모델 파일 필요

---

## 완료된 작업

### 1단계: 녹음 기능

- WAV 포맷 (16kHz, Mono, 16bit)
- 저장 경로: `/Android/data/kr.jm.voicesummary/files/`

### 2단계: Whisper STT 연동

- whisper.cpp NDK 직접 통합
- 모델: ggml-base.bin (142MB, assets 내장)
- 한국어 인식 지원
- 긴 오디오 청크 처리 (5분 단위) - OOM 방지

### 3단계: Room DB + Expandable UI

- Recording 엔티티 (filePath, fileName, createdAt, fileSize, transcription, summary)
- 녹음 목록에서 펼치기/닫기로 STT 결과 표시
- Long click으로 삭제 다이얼로그

### 4단계: Clean Architecture + MVVM 리팩토링

폴더 구조:
- `domain/` - model, repository interface
- `data/` - local (Room DB), repository impl
- `presentation/` - recording, list (Screen + ViewModel + UiState)
- `core/` - audio (AudioRecorder, AudioPlayer), whisper, llm
- `service/` - RecordingService (Foreground Service)

수동 DI:
- VoiceSummaryApp에서 의존성 생성
- ViewModel Factory 패턴으로 주입

Stateless UI:
- Screen은 UiState만 받아서 렌더링
- ViewModel이 모든 상태 관리

### 5단계: Foreground Service (백그라운드 처리)

RecordingService 구현:
- 녹음, STT, 요약 모두 백그라운드에서 처리
- 알림바 상태 표시: "녹음 중...", "텍스트 변환 중...", "AI 요약 중..."
- 화면 꺼져도 작업 계속됨
- 작업 완료 시 자동 서비스 종료

권한:
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_MICROPHONE
- POST_NOTIFICATIONS (Android 13+)

### 6단계: MediaPipe LLM 요약 (코드 완료)

- MediaPipe tasks-genai 라이브러리 추가
- LlmSummarizer 구현 (core/llm/)
- 요약 버튼 (✨ 아이콘) - STT 완료 후 활성화
- 모델: gemma-2b-it-gpu-int4.bin (약 1.5GB) - Kaggle에서 다운로드 필요

---

## 해결한 이슈

### JNI 패키지명 불일치
- 원인: WhisperLib.kt를 core/whisper/로 이동 후 JNI 함수명 불일치
- 해결: whisper_jni.cpp의 함수명을 `Java_kr_jm_voicesummary_core_whisper_*`로 변경

### OOM (Out of Memory)
- 원인: 긴 WAV 파일 전체를 메모리에 로드
- 해결: 5분 단위 청크로 나눠서 처리, RandomAccessFile로 필요한 부분만 읽기

### 백그라운드 녹음 끊김
- 원인: Activity 생명주기에 종속된 AudioRecorder
- 해결: Foreground Service로 녹음 처리

---

## 남은 작업

1. LLM 모델 파일 다운로드 및 테스트
   - Kaggle에서 gemma-2b-it-gpu-int4 다운로드
   - assets에 추가 또는 다운로드 기능 구현

2. 배포 시 모델 용량 문제
   - Whisper: 142MB (assets 내장 가능)
   - Gemma: 1.5GB (APK 크기 제한 초과)
   - 옵션: 앱 내장 (1.6GB 앱) 또는 선택적 다운로드

---

## 기술 참고

### 모델 파일 위치
- Whisper: `app/src/main/assets/ggml-base.bin`
- Gemma: `app/src/main/assets/gemma-2b-it-gpu-int4.bin` (추가 필요)

### 주요 파일
- `VoiceSummaryApp.kt` - 수동 DI
- `RecordingService.kt` - Foreground Service
- `WhisperTranscriber.kt` - STT 처리 (청크 단위)
- `LlmSummarizer.kt` - LLM 요약
- `whisper_jni.cpp` - JNI 바인딩
