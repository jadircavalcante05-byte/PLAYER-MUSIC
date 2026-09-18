package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val coverDrawableRes: Int = 0,
    val isFavorite: Boolean = false,
    val category: String = "Local",
    val contentUri: String = "",
    val mediaStoreId: Long = 0L,
    val albumArtUri: String = ""
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
