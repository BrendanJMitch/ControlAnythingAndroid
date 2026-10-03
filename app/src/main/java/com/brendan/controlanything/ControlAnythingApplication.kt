package com.brendan.controlanything

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.brendan.controlanything.data.device.DeviceRepository
import com.brendan.controlanything.data.device.InfoProblem
import com.brendan.controlanything.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class ControlAnythingApplication : Application() {

    @Inject lateinit var deviceRepository: DeviceRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        reportInfoProblems()
    }

    /**
     * Collected here rather than in a screen so problems are reported for as long as the
     * connection (app-scoped) can deliver them, whichever screen is showing. The toast only points
     * at logcat - the details are too long and too technical for it.
     */
    private fun reportInfoProblems() {
        applicationScope.launch {
            deviceRepository.infoProblems.collect { problem ->
                Log.w(TAG, problem.logMessage())
                val message = if (problem.rejected) R.string.info_rejected_toast else R.string.info_partial_toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ControlAnythingApplication, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun InfoProblem.logMessage(): String = buildString {
        appendLine(if (rejected) "Rejected info payload:" else "Info payload parsed with dropped entries:")
        problems.forEach { appendLine("  - $it") }
        append("Payload: ").append(raw)
    }

    private companion object {
        const val TAG = "ControlAnything"
    }
}
