package app.nophoneinbed.data

import android.content.Context

interface TrackingStateStorage {
    fun getArmed(): Boolean
    fun putArmed(value: Boolean)
}

class SharedPreferencesTrackingStateStorage(context: Context) : TrackingStateStorage {
    private val preferences = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)

    override fun getArmed(): Boolean = preferences.getBoolean(KEY_ARMED, false)

    override fun putArmed(value: Boolean) {
        preferences.edit().putBoolean(KEY_ARMED, value).apply()
    }

    companion object {
        private const val KEY_ARMED = "armed"
    }
}

class TrackingStateStore(private val storage: TrackingStateStorage) {
    constructor(context: Context) : this(SharedPreferencesTrackingStateStorage(context))

    fun isArmed(): Boolean = storage.getArmed()

    fun setArmed(value: Boolean) = storage.putArmed(value)
}
