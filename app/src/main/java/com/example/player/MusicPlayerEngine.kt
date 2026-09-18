package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerEngine {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0f)
    val progressMs: StateFlow<Float> = _progressMs.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var currentTrackList: List<Track> = emptyList()

    fun initPlayer(context: Context) {
        if (mediaController != null || controllerFuture != null) return

        val sessionToken = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )

        val future = MediaController.Builder(context.applicationContext, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                setupControllerListener(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context.applicationContext))
    }

    private fun setupControllerListener(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        if (controller.isPlaying) {
            startProgressUpdateLoop()
        }

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressUpdateLoop()
                } else {
                    progressJob?.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    skipNext(currentTrackList)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val trackFromList = currentTrackList.find { it.contentUri == item.localConfiguration?.uri?.toString() }
                    if (trackFromList != null) {
                        _currentTrack.value = trackFromList
                    }
                }
            }
        })
    }

    fun playTrack(track: Track, trackList: List<Track> = emptyList()) {
        if (_currentTrack.value?.id == track.id && _currentTrack.value?.contentUri == track.contentUri && track.contentUri.isNotBlank()) {
            togglePlayPause()
            return
        }

        _currentTrack.value = track
        if (trackList.isNotEmpty()) {
            currentTrackList = trackList
        }

        val controller = mediaController ?: return

        if (track.contentUri.isNotBlank()) {
            try {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(track.contentUri))
                    .setMediaId(track.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build()
                    )
                    .build()

                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
                _progressMs.value = 0f
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) {
                controller.seekTo(0)
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Float) {
        _progressMs.value = positionMs
        mediaController?.seekTo(positionMs.toLong())
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        mediaController?.shuffleModeEnabled = newShuffle
    }

    fun toggleRepeat() {
        val newRepeat = !_isRepeat.value
        _isRepeat.value = newRepeat
        mediaController?.repeatMode = if (newRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun skipNext(trackList: List<Track>) {
        val list = if (trackList.isNotEmpty()) trackList else currentTrackList
        if (list.isEmpty()) return
        val current = _currentTrack.value
        val currentIndex = list.indexOfFirst { it.contentUri == current?.contentUri }
        val nextIndex = if (_isShuffle.value) {
            (list.indices).random()
        } else if (currentIndex >= 0 && currentIndex < list.size - 1) {
            currentIndex + 1
        } else {
            0
        }
        playTrack(list[nextIndex], list)
    }

    fun skipPrevious(trackList: List<Track>) {
        val list = if (trackList.isNotEmpty()) trackList else currentTrackList
        if (list.isEmpty()) return
        val current = _currentTrack.value
        val currentIndex = list.indexOfFirst { it.contentUri == current?.contentUri }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playTrack(list[prevIndex], list)
    }

    private fun startProgressUpdateLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _progressMs.value = controller.currentPosition.toFloat()
                    }
                }
                delay(500)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        mediaController = null
    }
}
