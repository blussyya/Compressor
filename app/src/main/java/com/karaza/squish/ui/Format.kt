package com.karaza.squish.ui

fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / 1024.0 / 1024.0)

fun formatKbps(bps: Int): String = "${bps / 1000} kbps"

fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return formatDuration(totalSeconds)
}
