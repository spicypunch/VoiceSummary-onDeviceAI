package kr.jm.voicesummary.core.llm

enum class LlmModel(
    val displayName: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeDescription: String
) {
    GEMMA_2B_GPU(
        displayName = "Gemma 2B (GPU)",
        description = "GPU 가속, 빠름",
        downloadUrl = "https://huggingface.co/litert-community/Gemma-2B-IT/resolve/main/gemma-2b-it-gpu-int4.bin",
        fileName = "gemma-2b-it-gpu-int4.bin",
        sizeDescription = "~1.35GB"
    ),
    GEMMA_2B_CPU(
        displayName = "Gemma 2B (CPU)",
        description = "CPU 전용, 호환성 높음",
        downloadUrl = "https://huggingface.co/litert-community/Gemma-2B-IT/resolve/main/gemma-2b-it-cpu-int4.bin",
        fileName = "gemma-2b-it-cpu-int4.bin",
        sizeDescription = "~1.35GB"
    )
}
