package com.karaza.squish.data

/**
 * Manual override for the resolution ladder. AUTO keeps the original behavior
 * (highest rung that clears the bits-per-pixel floor); everything else pins the
 * output to a specific height instead of letting the bitrate pick it — useful when
 * you'd rather keep detail and accept a softer bitrate than get auto-downscaled.
 */
enum class ResolutionChoice(val label: String) {
    AUTO("Auto"),
    ORIGINAL("Original"),
    R1080("1080p"),
    R720("720p"),
    R480("480p");

    /** Target height for this choice, or null for AUTO (ladder decides). Caller caps to source height. */
    fun targetHeight(sourceHeight: Int): Int? = when (this) {
        AUTO -> null
        ORIGINAL -> sourceHeight
        R1080 -> 1080
        R720 -> 720
        R480 -> 480
    }
}
