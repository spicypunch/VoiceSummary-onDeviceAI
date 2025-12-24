# VoiceSummary 프로젝트 개발 기록

## 프로젝트 개요
- 패키지: `kr.jm.voicesummary`
- 목표: 온디바이스 AI 녹음기 앱 (완전 오프라인 STT)
- 핵심 가치: "서버 전송 0바이트" - 비행기 모드에서도 동작
- 타겟 기기: Nothing Phone 3a (Snapdragon 7s Gen 3)
- 기술 스택: Android Native (Kotlin) + Jetpack Compose

## 개발 로드맵

1. 녹음 기능 - 완료
2. STT 연동 - 완료 (Sherpa-ONNX + Whisper)
3. Room DB + UI - 완료
4. Clean Architecture + MVVM 리팩토링 - 완료
5. Foreground Service (백그라운드 처리) - 완료
6. 모델 선택 기능 - 완료
7. LLM 요약 - 제거 (온디바이스 LLM 호환성 문제)

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
- 최종: Sherpa-ONNX + Whisper 모델

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
- 다운로드된 모델은 표시

### 3단계: Room DB + Expandable UI

- Recording 엔티티 (filePath, fileName, createdAt, fileSize, transcription)
- 녹음 목록에서 펼치기/닫기로 STT 결과 표시
- Long click으로 삭제 다이얼로그
- 텍스트 선택 가능 + 복사 아이콘

### 4단계: Clean Architecture + MVVM 리팩토링

폴더 구조:
```
kr.jm.voicesummary/
├── domain/           # 비즈니스 로직
│   ├── model/        # Recording, SttModel
│   └── repository/   # RecordingRepository, AudioRepository, SttRepository (인터페이스)
├── data/             # 데이터 레이어
│   ├── local/        # AppDatabase, RecordingDao, RecordingEntity
│   ├── audio/        # AudioRepositoryImpl (녹음/재생)
│   ├── stt/          # SttRepositoryImpl (STT 다운로드/변환)
│   └── repository/   # RecordingRepositoryImpl
├── presentation/     # UI 레이어
│   ├── recording/    # RecordingScreen, RecordingViewModel, RecordingUiState
│   └── list/         # RecordingListScreen, RecordingListViewModel, RecordingListUiState
├── di/               # AppContainer (수동 의존성 주입)
├── service/          # RecordingService (Foreground Service)
└── ui/theme/         # Color, Theme, Type
```

수동 DI:
- AppContainer에서 의존성 생성
- ViewModel Factory 패턴으로 주입
- Hilt 제거 (수동 DI로 전환)

### 5단계: Foreground Service

RecordingService 구현:
- 녹음, STT 백그라운드에서 처리
- 알림바 상태 표시
- 화면 꺼져도 작업 계속됨
- ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE (녹음 시)
- ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC (STT/다운로드 시)

### 6단계: 모델 선택 UI

- 상단 배너에 현재 선택된 모델 표시
- "변경" 버튼으로 모델 선택 다이얼로그
- 다운로드 버튼으로 선택한 모델 다운로드
- 다운로드 진행률 + 압축 해제 상태 표시
- tar.bz2 압축 해제 (Apache Commons Compress)

### 7단계: LLM 요약 기능 (제거됨)

시도한 솔루션:
- MediaPipe + Gemma 2B GPU: OpenCL 에러 (`clSetPerfHintQCOM` undefined symbol)
- java-llama.cpp: Android arm64용 네이티브 라이브러리 미포함 (`libllama.so not found`)

제거 이유:
- 온디바이스 LLM이 Nothing Phone 3a (Snapdragon 7s Gen 3)에서 호환 안 됨
- MediaPipe는 OpenCL 사용 → Qualcomm GPU 드라이버 호환 문제
- java-llama.cpp는 데스크톱만 지원

기술적 배경:
- OpenCL: GPU 연산용 오래된 API, 제조사마다 구현 다름 (호환성 문제)
- Vulkan: 새로운 GPU API, 안드로이드 표준으로 호환성 좋음
- 현재 Vulkan 기반 온디바이스 LLM 솔루션이 부족함

---

## 주요 파일

### Domain
- `Recording.kt` - 녹음 도메인 모델
- `SttModel.kt` - STT 모델 enum (URL, 파일명 등)
- `RecordingRepository.kt` - 녹음 저장소 인터페이스
- `AudioRepository.kt` - 오디오 녹음/재생 인터페이스
- `SttRepository.kt` - STT 처리 인터페이스

### Data
- `AudioRepositoryImpl.kt` - 녹음/재생 구현
- `SttRepositoryImpl.kt` - STT 다운로드/변환 구현
- `RecordingRepositoryImpl.kt` - Room DB 연동

### Presentation
- `RecordingListScreen.kt` - 녹음 목록 + 모델 배너
- `RecordingListViewModel.kt` - 상태 관리
- `RecordingScreen.kt` - 녹음 화면

### DI
- `AppContainer.kt` - 의존성 주입 컨테이너

### Service
- `RecordingService.kt` - Foreground Service

---

## 모델 파일 위치

- Sherpa 모델: `filesDir/sherpa-models/{MODEL_NAME}/`
  - 예: `sherpa-models/WHISPER_BASE/base-encoder.int8.onnx`

---

## 의존성

```kotlin
// Sherpa-ONNX (AAR 직접 포함)
implementation(files("libs/sherpa-onnx-1.12.20.aar"))

// tar.bz2 압축 해제
implementation("org.apache.commons:commons-compress:1.26.0")

// Room DB
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)

// Jetpack Compose
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.compose.material.icons.extended)
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

### Foreground Service 권한 에러
- Android 14+ 에서 FOREGROUND_SERVICE_MICROPHONE 권한 필요
- AndroidManifest.xml에 권한 추가
- startForeground 시 ServiceInfo.FOREGROUND_SERVICE_TYPE 지정

### 온디바이스 LLM 호환성
- MediaPipe + Gemma: OpenCL 호환 문제로 실패
- java-llama.cpp: Android 미지원으로 실패
- 결론: LLM 기능 제거, STT만 유지

---

## 향후 개선 가능 사항

1. STT 정확도 개선
   - small/medium 모델 테스트
   - 속도 vs 정확도 트레이드오프 확인

2. Vulkan 기반 LLM 솔루션 등장 시 재검토
   - llama.cpp Vulkan 백엔드 안정화 대기
   - MLC LLM 등 대안 모니터링

3. UI/UX 개선
   - 녹음 파형 시각화
   - STT 실시간 스트리밍 (현재는 녹음 완료 후 처리)
