package kr.jm.voicesummary

import android.app.Application
import kr.jm.voicesummary.di.AppContainer

class VoiceSummaryApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
