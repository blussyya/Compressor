package com.karaza.squish.data

/**
 * Everything needed to describe one candidate export: resolution, bitrates, and the
 * estimated output size. Shared by the decision-screen previews and the real exporter
 * so the two can never drift apart — call [CompressionPlanner.plan] from both.
 */
data class CompressionPlan(
    val keepAudio: Boolean,
    val audioBps: Int,
    val videoBps: Int,
    val height: Int,
    val channelCount: Int,
    val estimatedBytes: Long,
    val targetBytes: Long,
)

/**
 * Pure bitrate/resolution math, no Android framework dependencies. Kept separate from
 * [com.karaza.squish.CompressionViewModel] so it's trivial to reason about and reuse
 * for the Compose preview cards.
 */
object CompressionPlanner {

    /** Hard ceiling for the output. 19 MB keeps you clear of the 20 MB wall. */
    const val DEFAULT_TARGET_BYTES = 19L * 1024 * 1024

    /** Offered in the target-size chip row, in MB. */
    val TARGET_SIZE_OPTIONS_MB = listOf(8, 16, 25, 50, 100)

    const val MONO_AUDIO_BPS = 64_000
    const val STEREO_AUDIO_BPS = 96_000

    /**
     * Bits-per-pixel floor. Below this, hardware H.264 goes mushy, so we drop
     * resolution instead of grinding the bitrate down further.
     * 0.015 is tuned for screen capture (flat colors, static regions, compresses
     * great). Raise toward 0.03 if you mostly squish camera footage.
     */
    private const val MIN_BPP = 0.015

    private const val ASSUMED_FPS = 30
    private val LADDER = intArrayOf(1080, 720, 540, 480, 360)

    private const val MIN_VIDEO_BPS = 150_000

    /** MP4 container/moov overhead, so longer files don't nudge over the ceiling. */
    private const val OVERHEAD_FRACTION = 0.02
    private const val MIN_OVERHEAD_BPS = 10_000

    fun audioBitrateFor(channelCount: Int): Int =
        if (channelCount <= 1) MONO_AUDIO_BPS else STEREO_AUDIO_BPS

    /** Largest rung on the ladder that still clears MIN_BPP at this bitrate. */
    fun pickHeight(videoBps: Int): Int {
        for (h in LADDER) {
            val w = h * 16 / 9
            if (videoBps.toDouble() / (w * h * ASSUMED_FPS) >= MIN_BPP) return h
        }
        return LADDER.last()
    }

    /** The next rung down from [height], or the same value if already at the bottom. */
    fun nextLowerHeight(height: Int): Int {
        val index = LADDER.indexOf(height)
        if (index == -1 || index == LADDER.lastIndex) return LADDER.last()
        return LADDER[index + 1]
    }

    fun plan(
        targetBytes: Long,
        durationSeconds: Long,
        keepAudio: Boolean,
        channelCount: Int,
    ): CompressionPlan {
        val safeDuration = durationSeconds.coerceAtLeast(1)
        val totalBps = (targetBytes * 8 / safeDuration).toInt()
        val overheadBps = (totalBps * OVERHEAD_FRACTION).toInt().coerceAtLeast(MIN_OVERHEAD_BPS)
        val audioBps = if (keepAudio) audioBitrateFor(channelCount) else 0
        val videoBps = (totalBps - audioBps - overheadBps).coerceAtLeast(MIN_VIDEO_BPS)
        val height = pickHeight(videoBps)
        val estimatedBytes = (videoBps.toLong() + audioBps + overheadBps) * safeDuration / 8
        return CompressionPlan(
            keepAudio = keepAudio,
            audioBps = audioBps,
            videoBps = videoBps,
            height = height,
            channelCount = channelCount,
            estimatedBytes = estimatedBytes,
            targetBytes = targetBytes,
        )
    }
}
