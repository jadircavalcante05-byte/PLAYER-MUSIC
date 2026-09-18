package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks ORDER BY id ASC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1")
    fun getFavoriteTracks(): Flow<List<Track>>

    @Query("SELECT * FROM playlists ORDER BY id ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<Playlist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("SELECT IFNULL(MAX(orderIndex), 0) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getNextOrderIndex(playlistId: Long): Int

    @Query("SELECT T.* FROM tracks T INNER JOIN playlist_tracks PT ON T.id = PT.trackId WHERE PT.playlistId = :playlistId ORDER BY PT.orderIndex ASC, T.id ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>

    @Query("UPDATE playlist_tracks SET orderIndex = :newIndex WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePlaylistTrackOrder(playlistId: Long, trackId: Long, newIndex: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("UPDATE playlists SET songCount = (SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = playlists.id) WHERE id = :playlistId")
    suspend fun updatePlaylistSongCount(playlistId: Long)

    @Update
    suspend fun updateTrack(track: Track)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun deleteTrackFromAllPlaylists(trackId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Query("SELECT id FROM tracks WHERE isFavorite = 1")
    suspend fun getFavoriteTrackIds(): List<Long>

    @Query("SELECT id FROM tracks")
    suspend fun getAllTrackIds(): List<Long>

    @Query("DELETE FROM tracks WHERE id NOT IN (:validIds)")
    suspend fun removeObsoleteTracks(validIds: List<Long>)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int
}
