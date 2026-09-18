package com.example.data

import android.content.Context
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(private val musicDao: MusicDao) {

    val allTracks: Flow<List<Track>> = musicDao.getAllTracks()
    val favoriteTracks: Flow<List<Track>> = musicDao.getFavoriteTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun loadTracksFromMediaStore(context: Context): Int = withContext(Dispatchers.IO) {
        val realTracks = MediaStoreScanner.scanAudioFiles(context)
        if (realTracks.isNotEmpty()) {
            val favoriteIds = musicDao.getFavoriteTrackIds()
            val existingIds = musicDao.getAllTrackIds()
            val validSet = realTracks.map { it.id }.toSet()
            val obsoleteIds = existingIds.filter { it !in validSet }
            obsoleteIds.chunked(500).forEach { chunk ->
                musicDao.removeObsoleteTracks(chunk)
            }
            val updatedTracks = realTracks.map { track ->
                if (favoriteIds.contains(track.id)) {
                    track.copy(isFavorite = true)
                } else {
                    track
                }
            }
            musicDao.insertTracks(updatedTracks)
        }
        realTracks.size
    }

    suspend fun seedInitialPlaylistsIfEmpty() {
        if (musicDao.getPlaylistCount() == 0) {
            val initialPlaylists = listOf(
                Playlist(
                    name = "Favoritas",
                    songCount = 0,
                    coverDrawableRes = R.drawable.img_cover_cyberpunk,
                    isUserCreated = false,
                    description = "Suas faixas salvas"
                ),
                Playlist(
                    name = "Recentes",
                    songCount = 0,
                    coverDrawableRes = R.drawable.img_cover_workout,
                    isUserCreated = false,
                    description = "Músicas tocadas recentemente"
                ),
                Playlist(
                    name = "Minha Playlist",
                    songCount = 0,
                    coverDrawableRes = R.drawable.img_cover_lofi,
                    isUserCreated = true,
                    description = "Criada por você"
                )
            )
            musicDao.insertPlaylists(initialPlaylists)
        }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> = musicDao.getTracksForPlaylist(playlistId)

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val nextIndex = musicDao.getNextOrderIndex(playlistId)
        musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(playlistId, trackId, nextIndex))
        musicDao.updatePlaylistSongCount(playlistId)
    }

    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTracks: List<Track>) {
        orderedTracks.forEachIndexed { index, track ->
            musicDao.updatePlaylistTrackOrder(playlistId, track.id, index)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        musicDao.removeTrackFromPlaylist(playlistId, trackId)
        musicDao.updatePlaylistSongCount(playlistId)
    }

    suspend fun insertTrack(track: Track) = musicDao.insertTrack(track)

    suspend fun toggleFavorite(track: Track) {
        musicDao.updateTrack(track.copy(isFavorite = !track.isFavorite))
    }

    suspend fun renameTrack(track: Track, newTitle: String, newArtist: String) {
        musicDao.updateTrack(track.copy(title = newTitle, artist = newArtist))
    }

    suspend fun deleteTrack(trackId: Long) {
        musicDao.deleteTrackFromAllPlaylists(trackId)
        musicDao.deleteTrack(trackId)
    }

    suspend fun createPlaylist(name: String, coverRes: Int): Long {
        val newPlaylist = Playlist(
            name = name,
            songCount = 0,
            coverDrawableRes = coverRes,
            isUserCreated = true
        )
        return musicDao.insertPlaylist(newPlaylist)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.deletePlaylist(playlistId)
    }
}
