package app.agentterm

import android.app.Application
import android.content.Context
import app.agentterm.config.ConfigRepository
import app.agentterm.sessions.SessionManager
import app.agentterm.ssh.ConnectionStore

class App : Application() {
    lateinit var config: ConfigRepository
        private set
    lateinit var connections: ConnectionStore
        private set
    lateinit var sessions: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        config = ConfigRepository(this)
        connections = ConnectionStore(this)
        sessions = SessionManager(this)
        installCrashLogger()
    }

    /** Writes fatal crashes to /Download/agentterm-crash.log via MediaStore (API 29+), */
    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = android.util.Log.getStackTraceString(throwable)
            android.util.Log.e("agentterm", "UNCAUGHT on ${thread.name}: $trace")
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "agentterm-crash.log")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                    }
                    val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(trace.toByteArray()) } }
                }
                java.io.File(cacheDir, "crash.log").writeText(trace)
            } catch (_: Exception) {}
            prev?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: App
            private set
        val appContext: Context get() = instance.applicationContext
    }
}