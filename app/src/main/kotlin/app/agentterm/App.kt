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
    }

    companion object {
        lateinit var instance: App
            private set
        val appContext: Context get() = instance.applicationContext
    }
}