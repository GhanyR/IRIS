package app.nophoneinbed.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagerActionTest {
    @Test
    fun parsesSupportedManagerActions() {
        assertEquals(ManagerAction.START, ManagerAction.fromIntentAction(ManagerAction.START_ACTION))
        assertEquals(ManagerAction.STOP, ManagerAction.fromIntentAction(ManagerAction.STOP_ACTION))
        assertEquals(ManagerAction.TEST_ALARM, ManagerAction.fromIntentAction(ManagerAction.TEST_ALARM_ACTION))
    }

    @Test
    fun rejectsUnknownOrMissingActions() {
        assertNull(ManagerAction.fromIntentAction(null))
        assertNull(ManagerAction.fromIntentAction("android.intent.action.MAIN"))
        assertNull(ManagerAction.fromIntentAction("app.nophoneinbed.action.UNKNOWN"))
    }
}
