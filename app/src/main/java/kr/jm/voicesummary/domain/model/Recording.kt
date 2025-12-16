package kr.jm.voicesummary.domain.model

data class Recording(
    val filePath: String,
    val fileName: String,
    val createdAt: Long,
    val fileSize: Long,
    val transcription: String? = null,
    val summary: String? = null
)
