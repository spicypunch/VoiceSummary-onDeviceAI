package kr.jm.voicesummary.core.whisper

import android.util.Log

class WhisperLib {

    companion object {
        private const val TAG = "WhisperLib"

        init {
            try {
                // 최적화된 라이브러리 먼저 시도
                System.loadLibrary("whisper_v8fp16_va")
                Log.i(TAG, "Loaded whisper_v8fp16_va (ARM64 optimized)")
            } catch (e: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("whisper_vfpv4")
                    Log.i(TAG, "Loaded whisper_vfpv4 (ARMv7 optimized)")
                } catch (e2: UnsatisfiedLinkError) {
                    System.loadLibrary("whisper_jni")
                    Log.i(TAG, "Loaded whisper_jni (default)")
                }
            }
        }
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun transcribe(contextPtr: Long, audioData: FloatArray, language: String): String
    external fun isModelLoaded(contextPtr: Long): Boolean
    external fun getSystemInfo(): String
}
