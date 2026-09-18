package com.example.data

data class AlbumItem(
    val name: String,
    val artist: String,
    val coverDrawableRes: Int,
    val songCount: Int,
    val tracks: List<Track>,
    val albumArtUri: String = ""
)
