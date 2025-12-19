package kr.jm.voicesummary.core.stt

enum class SttModel(
    val displayName: String,
    val description: String,
    val downloadUrl: String,
    val extractedDirName: String,
    val encoderFile: String,
    val decoderFile: String,
    val tokensFile: String,
    val sizeDescription: String
) {
    WHISPER_TINY(
        displayName = "Whisper Tiny",
        description = "가장 빠름, 정확도 낮음",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
        extractedDirName = "sherpa-onnx-whisper-tiny",
        encoderFile = "tiny-encoder.int8.onnx",
        decoderFile = "tiny-decoder.int8.onnx",
        tokensFile = "tiny-tokens.txt",
        sizeDescription = "~40MB"
    ),
    WHISPER_BASE(
        displayName = "Whisper Base",
        description = "빠름, 정확도 중간",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
        extractedDirName = "sherpa-onnx-whisper-base",
        encoderFile = "base-encoder.int8.onnx",
        decoderFile = "base-decoder.int8.onnx",
        tokensFile = "base-tokens.txt",
        sizeDescription = "~75MB"
    ),
    WHISPER_SMALL(
        displayName = "Whisper Small",
        description = "보통, 정확도 높음",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2",
        extractedDirName = "sherpa-onnx-whisper-small",
        encoderFile = "small-encoder.int8.onnx",
        decoderFile = "small-decoder.int8.onnx",
        tokensFile = "small-tokens.txt",
        sizeDescription = "~250MB"
    ),
    WHISPER_MEDIUM(
        displayName = "Whisper Medium",
        description = "느림, 정확도 매우 높음",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-medium.tar.bz2",
        extractedDirName = "sherpa-onnx-whisper-medium",
        encoderFile = "medium-encoder.int8.onnx",
        decoderFile = "medium-decoder.int8.onnx",
        tokensFile = "medium-tokens.txt",
        sizeDescription = "~750MB"
    )
}
