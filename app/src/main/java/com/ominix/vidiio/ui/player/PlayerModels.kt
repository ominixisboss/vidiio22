package com.ominix.vidiio.ui.player

import androidx.media3.common.Format

data class AudioTrackInfo(
    val name: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val format: Format
)

enum class GestureType {
    VOLUME, BRIGHTNESS, SEEK_FORWARD, SEEK_BACK, ASPECT_RATIO
}
