package app.nophoneinbed.manager

enum class ManagerAction {
    START,
    STOP,
    TEST_ALARM;

    companion object {
        const val START_ACTION = "app.nophoneinbed.action.MANAGER_START"
        const val STOP_ACTION = "app.nophoneinbed.action.MANAGER_STOP"
        const val TEST_ALARM_ACTION = "app.nophoneinbed.action.MANAGER_TEST_ALARM"

        fun fromIntentAction(action: String?): ManagerAction? = when (action) {
            START_ACTION -> START
            STOP_ACTION -> STOP
            TEST_ALARM_ACTION -> TEST_ALARM
            else -> null
        }
    }
}
