package app.nophoneinbed.runtime

import app.nophoneinbed.domain.BedVolumeProjection
import app.nophoneinbed.domain.PhoneEvidence
import app.nophoneinbed.domain.TrackerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackerSnapshot(
    val state: TrackerState = TrackerState.CLEAR,
    val detections: List<PhoneEvidence> = emptyList(),
    val projectedVolume: BedVolumeProjection? = null,
    val inferenceMs: Long = 0L,
    val analysisFps: Float = 0f,
    val thermalStatus: Int = 0,
    val faultReason: String? = null,
)

class TrackerStatusStore(initial: TrackerSnapshot = TrackerSnapshot()) {
    private val mutableStatus = MutableStateFlow(initial)
    val status: StateFlow<TrackerSnapshot> = mutableStatus.asStateFlow()

    fun update(snapshot: TrackerSnapshot) {
        mutableStatus.value = snapshot.copy(detections = snapshot.detections.toList())
    }
}

object TrackerRuntime {
    val statusStore = TrackerStatusStore()
    val previewStore = TrackerPreviewStore(minimumIntervalMs = 500L)
}
