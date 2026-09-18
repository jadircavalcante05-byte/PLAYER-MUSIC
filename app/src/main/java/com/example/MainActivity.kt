package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AddSongsToPlaylistSheet
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.BottomNavBar
import com.example.ui.components.DeleteTrackDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingSheet
import com.example.ui.components.RenameTrackDialog
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.NavTab

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MyApplicationTheme {
                MusicAppMainContent(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAndLoadMediaStoreTracks()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        try {
            val uris = mutableListOf<android.net.Uri>()
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) uris.add(uri)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val list = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    if (list != null) uris.addAll(list)
                }
                Intent.ACTION_VIEW -> {
                    intent.data?.let { uris.add(it) }
                }
            }

            intent.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    val itemUri = clipData.getItemAt(i).uri
                    if (itemUri != null && !uris.contains(itemUri)) {
                        uris.add(itemUri)
                    }
                }
            }

            if (uris.isNotEmpty()) {
                viewModel.handleSharedAudioUris(uris, contentResolver)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MusicAppMainContent(viewModel: MusicViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isPermissionsVisible by viewModel.isPermissionsScreenVisible.collectAsStateWithLifecycle()

    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val filteredPlaylists by viewModel.filteredPlaylists.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progressMs by viewModel.progressMs.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val isRepeat by viewModel.isRepeat.collectAsStateWithLifecycle()

    val isLibraryEmptyMode by viewModel.isLibraryEmptyMode.collectAsStateWithLifecycle()
    val isSplashScreenVisible by viewModel.isSplashScreenVisible.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedPlaylistFilter by viewModel.selectedPlaylistFilter.collectAsStateWithLifecycle()
    val isCreatePlaylistModalOpen by viewModel.isCreatePlaylistModalOpen.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()

    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsStateWithLifecycle()
    val isAddSongsToPlaylistOpen by viewModel.isAddSongsToPlaylistOpen.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
    val trackToRename by viewModel.trackToRename.collectAsStateWithLifecycle()
    val trackToDelete by viewModel.trackToDelete.collectAsStateWithLifecycle()
    val sharedAudioTrack by viewModel.sharedAudioTrack.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.handleSharedAudioUris(uris, context.contentResolver)
        }
    }

    LaunchedEffect(Unit) {
        val currentStatus = ContextCompat.checkSelfPermission(context, permissionToRequest)
        if (currentStatus != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permissionToRequest)
        } else {
            viewModel.onPermissionResult(true)
        }
    }

    if (isSplashScreenVisible) {
        SplashScreen()
    } else if (isPermissionsVisible) {
        PermissionsScreen(
            viewModel = viewModel,
            onBack = { viewModel.closePermissionsScreen() }
        )
    } else {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayerBar(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        progressMs = progressMs,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        onSkipPrevious = { viewModel.skipPrevious() },
                        onExpandNowPlaying = { viewModel.openNowPlayingExpanded() }
                    )
                    BottomNavBar(
                        currentTab = currentTab,
                        onTabSelected = {
                            viewModel.closePlaylistDetail()
                            viewModel.closeAlbumDetail()
                            viewModel.selectTab(it)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectedAlbum != null) {
                    AlbumDetailScreen(
                        album = selectedAlbum!!,
                        currentPlayingTrack = currentTrack,
                        isPlaying = isPlaying,
                        onBack = { viewModel.closeAlbumDetail() },
                        onTrackClick = { viewModel.playTrack(it, selectedAlbum!!.tracks) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistModal(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRenameTrack = { viewModel.openRenameTrackDialog(it) },
                        onDeleteTrack = { viewModel.openDeleteTrackDialog(it) }
                    )
                } else if (selectedPlaylist != null) {
                    PlaylistDetailScreen(
                        playlist = selectedPlaylist!!,
                        tracks = selectedPlaylistTracks,
                        currentPlayingTrack = currentTrack,
                        isPlaying = isPlaying,
                        onBack = { viewModel.closePlaylistDetail() },
                        onTrackClick = { viewModel.playTrack(it, selectedPlaylistTracks) },
                        onAddSongsClick = { viewModel.openAddSongsToPlaylistModal() },
                        onRemoveTrack = { viewModel.removeTrackFromPlaylist(selectedPlaylist!!, it) },
                        onAddToPlaylist = { viewModel.openAddToPlaylistModal(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRenameTrack = { viewModel.openRenameTrackDialog(it) },
                        onDeleteTrack = { viewModel.openDeleteTrackDialog(it) },
                        onMoveUp = { viewModel.moveTrackUpInPlaylist(selectedPlaylist!!, it) },
                        onMoveDown = { viewModel.moveTrackDownInPlaylist(selectedPlaylist!!, it) }
                    )
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tab_transition"
                    ) { targetTab ->
                        when (targetTab) {
                            NavTab.LIBRARY -> LibraryScreen(
                                viewModel = viewModel,
                                tracks = filteredTracks,
                                albums = albums,
                                currentPlayingTrack = currentTrack,
                                isPlaying = isPlaying,
                                isLibraryEmptyMode = isLibraryEmptyMode,
                                searchQuery = searchQuery,
                                selectedFilter = selectedFilter,
                                onTrackClick = { viewModel.playTrack(it) },
                                onAlbumClick = { viewModel.openAlbumDetail(it) },
                                onRefreshLibrary = { viewModel.checkAndLoadMediaStoreTracks() },
                                onOpenPermissions = { viewModel.openPermissionsScreen() }
                            )
                            NavTab.EXPLORE -> ExploreScreen(
                                viewModel = viewModel,
                                tracks = allTracks,
                                onTrackClick = { viewModel.playTrack(it) }
                            )
                            NavTab.SEARCH -> SearchScreen(
                                viewModel = viewModel,
                                searchQuery = searchQuery,
                                filteredTracks = filteredTracks,
                                onTrackClick = { viewModel.playTrack(it) }
                            )
                            NavTab.PLAYLISTS -> PlaylistsScreen(
                                viewModel = viewModel,
                                playlists = filteredPlaylists,
                                selectedFilter = selectedPlaylistFilter,
                                isCreateModalOpen = isCreatePlaylistModalOpen,
                                onPlaylistClick = { playlist ->
                                    viewModel.openPlaylistDetail(playlist)
                                }
                            )
                        }
                    }
                }
            }

            // Dialog: Rename Track
            trackToRename?.let { track ->
                RenameTrackDialog(
                    track = track,
                    onConfirmRename = { newTitle, newArtist ->
                        viewModel.confirmRenameTrack(track, newTitle, newArtist)
                    },
                    onDismiss = { viewModel.closeRenameTrackDialog() }
                )
            }

            // Dialog: Delete Track
            trackToDelete?.let { track ->
                DeleteTrackDialog(
                    track = track,
                    onConfirmDelete = { viewModel.confirmDeleteTrack(track) },
                    onDismiss = { viewModel.closeDeleteTrackDialog() }
                )
            }

            // Sheet: Add Single Track to a Playlist
            trackToAddToPlaylist?.let { track ->
                AddToPlaylistSheet(
                    track = track,
                    playlists = allPlaylists,
                    onSelectPlaylist = { playlist -> viewModel.addTrackToPlaylist(playlist, track) },
                    onCreateNewPlaylist = { viewModel.openCreatePlaylistModal() },
                    onDismiss = { viewModel.closeAddToPlaylistModal() }
                )
            }

            // Sheet: Add Multiple Songs to Current Selected Playlist
            if (isAddSongsToPlaylistOpen && selectedPlaylist != null) {
                AddSongsToPlaylistSheet(
                    playlist = selectedPlaylist!!,
                    allTracks = allTracks,
                    playlistTracks = selectedPlaylistTracks,
                    onToggleTrack = { track, isIn -> viewModel.toggleTrackInCurrentPlaylist(track, isIn) },
                    onPreviewTrack = { track -> viewModel.playTrack(track) },
                    onImportFiles = { audioPickerLauncher.launch("audio/*") },
                    onDismiss = { viewModel.closeAddSongsToPlaylistModal() }
                )
            }

            // Shared Audio Floating Banner
            sharedAudioTrack?.let { track ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("shared_audio_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ÁUDIO COMPARTILHADO",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = track.title,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { viewModel.openAddToPlaylistModal(track) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Adicionar à Playlist", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { viewModel.dismissSharedAudioBanner() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Expanded Now Playing Sheet Overlay
            if (isNowPlayingExpanded && currentTrack != null) {
                NowPlayingSheet(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    progressMs = progressMs,
                    isShuffle = isShuffle,
                    isRepeat = isRepeat,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.skipNext() },
                    onSkipPrevious = { viewModel.skipPrevious() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddToPlaylist = { viewModel.openAddToPlaylistModal(it) },
                    onDismiss = { viewModel.closeNowPlayingExpanded() }
                )
            }
        }
    }
}
