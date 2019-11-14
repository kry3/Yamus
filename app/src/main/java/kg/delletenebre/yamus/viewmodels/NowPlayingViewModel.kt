package kg.delletenebre.yamus.viewmodels

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.*
import kg.delletenebre.yamus.R
import kg.delletenebre.yamus.common.EMPTY_PLAYBACK_STATE
import kg.delletenebre.yamus.common.MediaSessionConnection
import kg.delletenebre.yamus.common.NOTHING_PLAYING
import kg.delletenebre.yamus.media.extensions.currentPlayBackPosition
import kg.delletenebre.yamus.media.extensions.isPlaying

class NowPlayingViewModel(
        app: Application,
        mediaSessionConnection: MediaSessionConnection
) : AndroidViewModel(app) {

    val track = MutableLiveData<MediaMetadataCompat?>()
    val position = MutableLiveData<Long>().apply {
        postValue(0L)
    }

    val playPauseDrawable = MutableLiveData<Int>().apply {
        postValue(R.drawable.ic_pause)
    }

    val likeUnlikeDrawable = MutableLiveData<Int>().apply {
        postValue(R.drawable.ic_favorite_border)
    }


    var playbackState = MutableLiveData<PlaybackStateCompat>().apply {
        postValue(EMPTY_PLAYBACK_STATE)
    }

    private var updatePosition = true
    private val handler = Handler(Looper.getMainLooper())

    /**
     * When the session's [PlaybackStateCompat] changes, the [mediaItems] need to be updated
     * so the correct [MediaItemData.playbackRes] is displayed on the active item.
     * (i.e.: play/pause button or blank)
     */
    private val playbackStateObserver = Observer<PlaybackStateCompat> {
        val metadata = mediaSessionConnection.nowPlaying.value ?: NOTHING_PLAYING
        updateState(it ?: EMPTY_PLAYBACK_STATE, metadata)
    }

    /**
     * When the session's [MediaMetadataCompat] changes, the [mediaItems] need to be updated
     * as it means the currently active item has changed. As a result, the new, and potentially
     * old item (if there was one), both need to have their [MediaItemData.playbackRes]
     * changed. (i.e.: play/pause button or blank)
     */
    private val mediaMetadataObserver = Observer<MediaMetadataCompat> {
        updateState(playbackState.value!!, it)
    }

    /**
     * Because there's a complex dance between this [ViewModel] and the [MediaSessionConnection]
     * (which is wrapping a [MediaBrowserCompat] object), the usual guidance of using
     * [Transformations] doesn't quite work.
     *
     * Specifically there's three things that are watched that will cause the single piece of
     * [LiveData] exposed from this class to be updated.
     *
     * [MediaSessionConnection.playbackState] changes state based on the playback state of
     * the player, which can change the [MediaItemData.playbackRes]s in the list.
     *
     * [MediaSessionConnection.nowPlaying] changes based on the item that's being played,
     * which can also change the [MediaItemData.playbackRes]s in the list.
     */
    private val mediaSessionConnection = mediaSessionConnection.also {
        it.playbackState.observeForever(playbackStateObserver)
        it.nowPlaying.observeForever(mediaMetadataObserver)
        checkPlaybackPosition()
    }

    /**
     * Internal function that recursively calls itself every [POSITION_UPDATE_INTERVAL_MILLIS] ms
     * to check the current playback position and updates the corresponding LiveData object when it
     * has changed.
     */
    private fun checkPlaybackPosition(): Boolean = handler.postDelayed({
        val currPosition = playbackState.value!!.currentPlayBackPosition
        if (position.value != currPosition)
            position.postValue(currPosition)
        if (updatePosition)
            checkPlaybackPosition()
    }, POSITION_UPDATE_INTERVAL_MILLIS)

    /**
     * Since we use [LiveData.observeForever] above (in [mediaSessionConnection]), we want
     * to call [LiveData.removeObserver] here to prevent leaking resources when the [ViewModel]
     * is not longer in use.
     *
     * For more details, see the kdoc on [mediaSessionConnection] above.
     */
    override fun onCleared() {
        super.onCleared()

        // Remove the permanent observers from the MediaSessionConnection.
        mediaSessionConnection.playbackState.removeObserver(playbackStateObserver)
        mediaSessionConnection.nowPlaying.removeObserver(mediaMetadataObserver)

        // Stop updating the position
        updatePosition = false
    }

    private fun updateState(
            playbackState: PlaybackStateCompat,
            mediaMetadata: MediaMetadataCompat
    ) {
        this.playbackState.value = playbackState
        // Only update media item once we have duration available
//        if (mediaMetadata.duration != 0L) {
//            val track = CurrentPlaylist.tracks.find {
//                it.uniqueId == mediaMetadata.uniqueId
//            }
//            this.track.postValue(track)
//        }

        // Update the media button resource ID
        playPauseDrawable.postValue(
                when (playbackState.isPlaying) {
                    true -> R.drawable.avd_play_pause
                    else -> R.drawable.avd_pause_play
                }
        )
    }

    class Factory(
            private val app: Application,
            private val mediaSessionConnection: MediaSessionConnection
    ) : ViewModelProvider.NewInstanceFactory() {

        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return NowPlayingViewModel(app, mediaSessionConnection) as T
        }
    }
}

private const val POSITION_UPDATE_INTERVAL_MILLIS = 100L