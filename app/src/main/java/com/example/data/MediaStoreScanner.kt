package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.R

object MediaStoreScanner {

    fun scanAudioFiles(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} >= 3000 OR ${MediaStore.Audio.Media.TITLE} != ''"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                val coverDrawables = listOf(
                    R.drawable.img_cover_cyberpunk,
                    R.drawable.img_cover_lofi,
                    R.drawable.img_cover_vintage,
                    R.drawable.img_cover_workout
                )
                var coverIdx = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Sem Título"
                    val artist = cursor.getString(artistColumn).let {
                        if (it.isNullOrBlank() || it == "<unknown>") "Artista Desconhecido" else it
                    }
                    val album = cursor.getString(albumColumn).let {
                        if (it.isNullOrBlank() || it == "<unknown>") "Álbum Desconhecido" else it
                    }
                    val durationMs = cursor.getLong(durationColumn)
                    val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else 0L
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val albumArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else {
                        ""
                    }

                    val coverRes = coverDrawables[coverIdx % coverDrawables.size]
                    coverIdx++

                    tracks.add(
                        Track(
                            id = id,
                            mediaStoreId = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            coverDrawableRes = coverRes,
                            contentUri = contentUri,
                            category = "Local",
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tracks
    }
}
