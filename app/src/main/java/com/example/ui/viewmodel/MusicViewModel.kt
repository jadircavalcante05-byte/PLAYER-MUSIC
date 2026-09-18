package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AlbumItem
import com.example.data.AppDatabase
import com.example.data.MusicRepository
import com.example.data.Playlist
import com.example.data.Track
import com.example.player.MusicPlayerEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavTab {
    LIBRARY, EXPLORE, SEARCH, PLAYLISTS
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository
    val playerEngine = MusicPlayerEngine()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MusicRepository(db.musicDao())
        playerEngine.initPlayer(application)
        viewModelScope.launch {
            repository.seedInitialPlaylistsIfEmpty()
            checkAndLoadMediaStoreTracks()
        }
        viewModelScope.launch {
            delay(1800L)
            _isSplashScreenVisible.value = false
        }
    }

    // Splash Screen State
    private val _isSplashScreenVisible = MutableStateFlow(true)
    val isSplashScreenVisible: StateFlow<Boolean> = _isSplashScreenVisible.asStateFlow()

    // Active Navigation Tab
    private val _currentTab = MutableStateFlow(NavTab.LIBRARY)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    // Permission state
    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    // Library Mode: Empty vs Tracks List
    private val _isLibraryEmptyMode = MutableStateFlow(false)
    val isLibraryEmptyMode: StateFlow<Boolean> = _isLibraryEmptyMode.asStateFlow()

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filter Chip ("All Tracks", "Albums", "Artists", "Recent")
    private val _selectedFilter = MutableStateFlow("All Tracks")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    // Playlist Filter Chip ("Tudo", "Recentes", "Criadas por mim", "Seguindo")
    private val _selectedPlaylistFilter = MutableStateFlow("Tudo")
    val selectedPlaylistFilter: StateFlow<String> = _selectedPlaylistFilter.asStateFlow()

    // Modals & Bottom Sheets
    private val _isCreatePlaylistModalOpen = MutableStateFlow(false)
    val isCreatePlaylistModalOpen: StateFlow<Boolean> = _isCreatePlaylistModalOpen.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isPermissionsScreenVisible = MutableStateFlow(false)
    val isPermissionsScreenVisible: StateFlow<Boolean> = _isPermissionsScreenVisible.asStateFlow()

    // Playlist Detail View State
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylist
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList())
            else repository.getTracksForPlaylist(playlist.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Add Track To Playlist Modal State (from 3-dots or + icon)
    private val _trackToAddToPlaylist = MutableStateFlow<Track?>(null)
    val trackToAddToPlaylist: StateFlow<Track?> = _trackToAddToPlaylist.asStateFlow()

    // Add Songs To Selected Playlist Modal State (from Playlist Detail screen)
    private val _isAddSongsToPlaylistOpen = MutableStateFlow(false)
    val isAddSongsToPlaylistOpen: StateFlow<Boolean> = _isAddSongsToPlaylistOpen.asStateFlow()

    // Shared Audio Track State (from external Share/Open intents)
    private val _sharedAudioTrack = MutableStateFlow<Track?>(null)
    val sharedAudioTrack: StateFlow<Track?> = _sharedAudioTrack.asStateFlow()

    // Album Detail State
    private val _selectedAlbum = MutableStateFlow<AlbumItem?>(null)
    val selectedAlbum: StateFlow<AlbumItem?> = _selectedAlbum.asStateFlow()

    // Rename Track Modal State
    private val _trackToRename = MutableStateFlow<Track?>(null)
    val trackToRename: StateFlow<Track?> = _trackToRename.asStateFlow()

    // Delete Track Modal State
    private val _trackToDelete = MutableStateFlow<Track?>(null)
    val trackToDelete: StateFlow<Track?> = _trackToDelete.asStateFlow()

    // Tracks Flow
    val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Albums Flow (grouped from all tracks)
    val albums: StateFlow<List<AlbumItem>> = allTracks.map { tracks ->
        tracks.groupBy { it.album.ifBlank { "Álbum Desconhecido" } }
            .map { (albumName, albumTracks) ->
                AlbumItem(
                    name = albumName,
                    artist = albumTracks.firstOrNull()?.artist ?: "Vários Artistas",
                    coverDrawableRes = albumTracks.firstOrNull()?.coverDrawableRes ?: R.drawable.img_cover_cyberpunk,
                    songCount = albumTracks.size,
                    tracks = albumTracks,
                    albumArtUri = albumTracks.firstOrNull()?.albumArtUri ?: ""
                )
            }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Tracks
    val filteredTracks: StateFlow<List<Track>> = combine(allTracks, searchQuery, selectedFilter) { tracks, query, filter ->
        var list = if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
        when (filter) {
            "Recent", "Recentes" -> list.reversed()
            "Albums", "Álbuns" -> list.sortedBy { it.album }
            "Artists", "Artistas" -> list.sortedBy { it.artist }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlists Flow
    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Playlists
    val filteredPlaylists: StateFlow<List<Playlist>> = combine(allPlaylists, selectedPlaylistFilter) { playlists, filter ->
        when (filter) {
            "Recentes" -> playlists.reversed()
            "Criadas por mim" -> playlists.filter { it.isUserCreated }
            "Seguindo" -> playlists.filter { !it.isUserCreated }
            else -> playlists
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player States from Engine
    val currentTrack = playerEngine.currentTrack
    val isPlaying = playerEngine.isPlaying
    val progressMs = playerEngine.progressMs
    val isShuffle = playerEngine.isShuffle
    val isRepeat = playerEngine.isRepeat

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setPlaylistFilter(filter: String) {
        _selectedPlaylistFilter.value = filter
    }

    fun toggleLibraryEmptyMode() {
        _isLibraryEmptyMode.value = !_isLibraryEmptyMode.value
    }

    fun openCreatePlaylistModal() {
        _isCreatePlaylistModalOpen.value = true
    }

    fun closeCreatePlaylistModal() {
        _isCreatePlaylistModalOpen.value = false
    }

    fun openNowPlayingExpanded() {
        _isNowPlayingExpanded.value = true
    }

    fun closeNowPlayingExpanded() {
        _isNowPlayingExpanded.value = false
    }

    fun openPermissionsScreen() {
        _isPermissionsScreenVisible.value = true
    }

    fun closePermissionsScreen() {
        _isPermissionsScreenVisible.value = false
    }

    fun checkAndLoadMediaStoreTracks() {
        viewModelScope.launch {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val hasPerm = ContextCompat.checkSelfPermission(
                getApplication(),
                permission
            ) == PackageManager.PERMISSION_GRANTED

            _hasStoragePermission.value = hasPerm
            if (hasPerm) {
                val count = repository.loadTracksFromMediaStore(getApplication())
                _isLibraryEmptyMode.value = count == 0
            } else {
                _isLibraryEmptyMode.value = true
                _isPermissionsScreenVisible.value = false
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _hasStoragePermission.value = isGranted
        _isPermissionsScreenVisible.value = false
        if (isGranted) {
            viewModelScope.launch {
                val count = repository.loadTracksFromMediaStore(getApplication())
                _isLibraryEmptyMode.value = count == 0
            }
        } else {
            _isLibraryEmptyMode.value = true
        }
    }

    fun grantPermission() {
        _hasStoragePermission.value = true
        _isPermissionsScreenVisible.value = false
        viewModelScope.launch {
            val count = repository.loadTracksFromMediaStore(getApplication())
            _isLibraryEmptyMode.value = count == 0
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    fun createPlaylist(name: String, selectedCoverRes: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cover = if (selectedCoverRes != 0) selectedCoverRes else R.drawable.img_cover_cyberpunk
            val newId = repository.createPlaylist(name, cover)
            val newPlaylist = Playlist(
                id = newId,
                name = name,
                songCount = 0,
                coverDrawableRes = cover,
                isUserCreated = true
            )
            openPlaylistDetail(newPlaylist)
            closeCreatePlaylistModal()
        }
    }

    fun openPlaylistDetail(playlist: Playlist) {
        _selectedPlaylist.value = playlist
    }

    fun closePlaylistDetail() {
        _selectedPlaylist.value = null
    }

    fun openAlbumDetail(album: AlbumItem) {
        _selectedAlbum.value = album
    }

    fun closeAlbumDetail() {
        _selectedAlbum.value = null
    }

    fun openRenameTrackDialog(track: Track) {
        _trackToRename.value = track
    }

    fun closeRenameTrackDialog() {
        _trackToRename.value = null
    }

    fun confirmRenameTrack(track: Track, newTitle: String, newArtist: String) {
        viewModelScope.launch {
            repository.renameTrack(track, newTitle, newArtist)
            closeRenameTrackDialog()
        }
    }

    fun openDeleteTrackDialog(track: Track) {
        _trackToDelete.value = track
    }

    fun closeDeleteTrackDialog() {
        _trackToDelete.value = null
    }

    fun confirmDeleteTrack(track: Track) {
        viewModelScope.launch {
            repository.deleteTrack(track.id)
            closeDeleteTrackDialog()
        }
    }

    fun openAddToPlaylistModal(track: Track) {
        _trackToAddToPlaylist.value = track
    }

    fun closeAddToPlaylistModal() {
        _trackToAddToPlaylist.value = null
    }

    fun addTrackToPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlist.id, track.id)
            closeAddToPlaylistModal()
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlist.id, track.id)
        }
    }

    fun openAddSongsToPlaylistModal() {
        _isAddSongsToPlaylistOpen.value = true
    }

    fun closeAddSongsToPlaylistModal() {
        _isAddSongsToPlaylistOpen.value = false
    }

    fun toggleTrackInCurrentPlaylist(track: Track, isCurrentlyIn: Boolean) {
        val currentP = _selectedPlaylist.value ?: return
        viewModelScope.launch {
            if (isCurrentlyIn) {
                repository.removeTrackFromPlaylist(currentP.id, track.id)
            } else {
                repository.addTrackToPlaylist(currentP.id, track.id)
            }
        }
    }

    fun handleSharedAudioUris(uris: List<android.net.Uri>, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            val addedTracks = mutableListOf<Track>()
            val currentPlaylist = _selectedPlaylist.value
            for ((index, uri) in uris.withIndex()) {
                var title = "Música Importada ${if (uris.size > 1) "${index + 1}" else ""}".trim()
                var artist = "Áudio Externo"
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) {
                                    title = name.substringBeforeLast(".")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val existing = allTracks.value.firstOrNull { it.contentUri == uri.toString() || (it.title.equals(title, ignoreCase = true) && it.title != "Sem Título") }
                val trackToPlay = if (existing != null) {
                    existing
                } else {
                    val newTrack = Track(
                        mediaStoreId = System.currentTimeMillis() + index,
                        title = title,
                        artist = artist,
                        album = "Compartilhado",
                        durationMs = 180000L,
                        coverDrawableRes = R.drawable.img_cover_cyberpunk,
                        contentUri = uri.toString(),
                        category = "Compartilhado"
                    )
                    val insertedId = repository.insertTrack(newTrack)
                    val savedTrack = newTrack.copy(id = insertedId)
                    savedTrack
                }
                addedTracks.add(trackToPlay)

                if (currentPlaylist != null) {
                    repository.addTrackToPlaylist(currentPlaylist.id, trackToPlay.id)
                }
            }

            if (addedTracks.isNotEmpty()) {
                val firstTrack = addedTracks.first()
                _sharedAudioTrack.value = firstTrack
                playTrack(firstTrack, addedTracks)
                openNowPlayingExpanded()
            }
        }
    }

    fun handleSharedAudioUri(uri: android.net.Uri, title: String, artist: String) {
        viewModelScope.launch {
            val currentPlaylist = _selectedPlaylist.value
            val existing = allTracks.value.firstOrNull { it.contentUri == uri.toString() || it.title.equals(title, ignoreCase = true) }
            val trackToPlay = if (existing != null) {
                existing
            } else {
                val newTrack = Track(
                    mediaStoreId = System.currentTimeMillis(),
                    title = title,
                    artist = artist,
                    album = "Compartilhado",
                    durationMs = 180000L,
                    coverDrawableRes = R.drawable.img_cover_cyberpunk,
                    contentUri = uri.toString(),
                    category = "Compartilhado"
                )
                val insertedId = repository.insertTrack(newTrack)
                val savedTrack = newTrack.copy(id = insertedId)
                savedTrack
            }
            if (currentPlaylist != null) {
                repository.addTrackToPlaylist(currentPlaylist.id, trackToPlay.id)
            }
            _sharedAudioTrack.value = trackToPlay
            playTrack(trackToPlay, listOf(trackToPlay))
            openNowPlayingExpanded()
        }
    }

    fun dismissSharedAudioBanner() {
        _sharedAudioTrack.value = null
    }

    fun moveTrackUpInPlaylist(playlist: Playlist, track: Track) {
        val currentTracks = selectedPlaylistTracks.value.toMutableList()
        val idx = currentTracks.indexOfFirst { it.id == track.id }
        if (idx > 0) {
            val item = currentTracks.removeAt(idx)
            currentTracks.add(idx - 1, item)
            viewModelScope.launch {
                repository.reorderPlaylistTracks(playlist.id, currentTracks)
            }
        }
    }

    fun moveTrackDownInPlaylist(playlist: Playlist, track: Track) {
        val currentTracks = selectedPlaylistTracks.value.toMutableList()
        val idx = currentTracks.indexOfFirst { it.id == track.id }
        if (idx >= 0 && idx < currentTracks.size - 1) {
            val item = currentTracks.removeAt(idx)
            currentTracks.add(idx + 1, item)
            viewModelScope.launch {
                repository.reorderPlaylistTracks(playlist.id, currentTracks)
            }
        }
    }

    fun playTrack(track: Track, trackList: List<Track>? = null) {
        val queue = trackList ?: allTracks.value
        playerEngine.playTrack(track, queue)
    }

    fun togglePlayPause() {
        playerEngine.togglePlayPause()
    }

    fun skipNext() {
        playerEngine.skipNext(emptyList())
    }

    fun skipPrevious() {
        playerEngine.skipPrevious(emptyList())
    }

    fun seekTo(positionMs: Float) {
        playerEngine.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playerEngine.toggleShuffle()
    }

    fun toggleRepeat() {
        playerEngine.toggleRepeat()
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine.release()
    }
}
