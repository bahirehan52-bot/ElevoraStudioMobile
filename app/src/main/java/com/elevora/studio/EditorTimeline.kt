package com.elevora.studio

/**
 * Platform-independent editing model adapted from OpenCut's timeline concepts.
 * UI and Media3 adapters consume this model; this file performs no rendering.
 */
enum class TimelineTrackKind { VIDEO, AUDIO, TEXT, OVERLAY, EFFECT }

enum class TimelineItemKind { VIDEO, AUDIO, TEXT, IMAGE, STICKER, EFFECT }

data class TimelineEffect(
    val id: String,
    val type: String,
    val enabled: Boolean = true,
    val parameters: Map<String, Float> = emptyMap()
)

enum class MaskShape { RECTANGLE, ELLIPSE, LINEAR }

data class TimelineMask(
    val id: String,
    val shape: MaskShape,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotationDegrees: Float = 0f,
    val feather: Float = 0f,
    val inverted: Boolean = false,
    val enabled: Boolean = true
) {
    init {
        require(width > 0f && height > 0f) { "Mask dimensions must be positive." }
        require(feather >= 0f) { "Mask feather cannot be negative." }
    }
}

data class TimelineItem(
    val id: String,
    val kind: TimelineItemKind,
    val source: String,
    val startMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val effects: List<TimelineEffect> = emptyList(),
    val masks: List<TimelineMask> = emptyList()
) {
    val durationMs: Long get() = sourceOutMs - sourceInMs
    val endMs: Long get() = startMs + durationMs

    init {
        require(id.isNotBlank()) { "Timeline item id cannot be blank." }
        require(startMs >= 0L) { "Timeline start cannot be negative." }
        require(sourceInMs >= 0L && sourceOutMs > sourceInMs) {
            "Timeline source range is invalid."
        }
    }
}

data class TimelineTrack(
    val id: String,
    val name: String,
    val kind: TimelineTrackKind,
    val muted: Boolean = false,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val items: List<TimelineItem> = emptyList()
)

data class EditorTimeline(val tracks: List<TimelineTrack> = emptyList()) {
    fun track(trackId: String): TimelineTrack? = tracks.firstOrNull { it.id == trackId }

    fun addTrack(track: TimelineTrack): EditorTimeline {
        require(tracks.none { it.id == track.id }) { "Track id already exists." }
        return copy(tracks = tracks + track)
    }

    fun updateItem(trackId: String, item: TimelineItem): EditorTimeline =
        mapTrack(trackId) { track ->
            require(!track.locked) { "Track is locked." }
            require(track.items.any { it.id == item.id }) { "Timeline item not found." }
            track.copy(items = track.items.map { if (it.id == item.id) item else it })
        }

    fun moveItem(trackId: String, itemId: String, newStartMs: Long): EditorTimeline {
        require(newStartMs >= 0L) { "New start time cannot be negative." }
        return mapTrack(trackId) { track ->
            require(!track.locked) { "Track is locked." }
            track.copy(items = track.items.map {
                if (it.id == itemId) it.copy(startMs = newStartMs) else it
            })
        }
    }

    fun splitItem(trackId: String, itemId: String, splitAtTimelineMs: Long, newRightId: String): EditorTimeline =
        mapTrack(trackId) { track ->
            require(!track.locked) { "Track is locked." }
            require(track.items.none { it.id == newRightId }) { "New item id already exists." }
            val original = track.items.firstOrNull { it.id == itemId }
                ?: throw IllegalArgumentException("Timeline item not found.")
            require(splitAtTimelineMs > original.startMs && splitAtTimelineMs < original.endMs) {
                "Split point must be inside the clip."
            }
            val leftDuration = splitAtTimelineMs - original.startMs
            val sourceSplit = original.sourceInMs + leftDuration
            val left = original.copy(sourceOutMs = sourceSplit)
            val right = original.copy(
                id = newRightId,
                startMs = splitAtTimelineMs,
                sourceInMs = sourceSplit
            )
            track.copy(items = track.items.flatMap { if (it.id == itemId) listOf(left, right) else listOf(it) })
        }

    fun removeItem(trackId: String, itemId: String): EditorTimeline =
        mapTrack(trackId) { track ->
            require(!track.locked) { "Track is locked." }
            track.copy(items = track.items.filterNot { it.id == itemId })
        }

    fun addEffect(trackId: String, itemId: String, effect: TimelineEffect): EditorTimeline =
        changeItem(trackId, itemId) { item ->
            require(item.effects.none { it.id == effect.id }) { "Effect id already exists." }
            item.copy(effects = item.effects + effect)
        }

    fun updateEffect(trackId: String, itemId: String, effect: TimelineEffect): EditorTimeline =
        changeItem(trackId, itemId) { item ->
            require(item.effects.any { it.id == effect.id }) { "Effect not found." }
            item.copy(effects = item.effects.map { if (it.id == effect.id) effect else it })
        }

    fun removeEffect(trackId: String, itemId: String, effectId: String): EditorTimeline =
        changeItem(trackId, itemId) { it.copy(effects = it.effects.filterNot { effect -> effect.id == effectId }) }

    fun addMask(trackId: String, itemId: String, mask: TimelineMask): EditorTimeline =
        changeItem(trackId, itemId) { item ->
            require(item.masks.none { it.id == mask.id }) { "Mask id already exists." }
            item.copy(masks = item.masks + mask)
        }

    fun updateMask(trackId: String, itemId: String, mask: TimelineMask): EditorTimeline =
        changeItem(trackId, itemId) { item ->
            require(item.masks.any { it.id == mask.id }) { "Mask not found." }
            item.copy(masks = item.masks.map { if (it.id == mask.id) mask else it })
        }

    fun removeMask(trackId: String, itemId: String, maskId: String): EditorTimeline =
        changeItem(trackId, itemId) { it.copy(masks = it.masks.filterNot { mask -> mask.id == maskId }) }

    private fun changeItem(
        trackId: String,
        itemId: String,
        transform: (TimelineItem) -> TimelineItem
    ): EditorTimeline = mapTrack(trackId) { track ->
        require(!track.locked) { "Track is locked." }
        val target = track.items.firstOrNull { it.id == itemId }
            ?: throw IllegalArgumentException("Timeline item not found.")
        track.copy(items = track.items.map { if (it.id == itemId) transform(target) else it })
    }

    private fun mapTrack(trackId: String, transform: (TimelineTrack) -> TimelineTrack): EditorTimeline {
        require(tracks.any { it.id == trackId }) { "Track not found." }
        return copy(tracks = tracks.map { if (it.id == trackId) transform(it) else it })
    }
}
