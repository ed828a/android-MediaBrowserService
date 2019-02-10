/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediasession.ui

import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import com.example.android.mediasession.R
import com.example.android.mediasession.client.MediaBrowserHelper
import com.example.android.mediasession.service.MusicService
import com.example.android.mediasession.service.contentcatalogs.MusicLibrary

class MainActivity : AppCompatActivity() {

    private var mAlbumArt: ImageView? = null
    private var mTitleTextView: TextView? = null
    private var mArtistTextView: TextView? = null
    private var mMediaControlsImage: ImageView? = null
    private var mSeekBarAudio: MediaSeekBar? = null

    private var mMediaBrowserHelper: MediaBrowserHelper? = null

    private var mIsPlaying: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTitleTextView = findViewById(R.id.song_title)
        mArtistTextView = findViewById(R.id.song_artist)
        mAlbumArt = findViewById(R.id.album_art)
        mMediaControlsImage = findViewById(R.id.media_controls)
        mSeekBarAudio = findViewById(R.id.seekbar_audio)

        val clickListener = ClickListener()
        findViewById<View>(R.id.button_previous).setOnClickListener(clickListener)
        findViewById<View>(R.id.button_play).setOnClickListener(clickListener)
        findViewById<View>(R.id.button_next).setOnClickListener(clickListener)

        mMediaBrowserHelper = MediaBrowserConnection(this)
        mMediaBrowserHelper!!.registerCallback(MediaBrowserListener())
    }

    public override fun onStart() {
        super.onStart()
        mMediaBrowserHelper!!.onStart()
    }

    public override fun onStop() {
        super.onStop()
        mSeekBarAudio!!.disconnectController()
        mMediaBrowserHelper!!.onStop()
    }

    /**
     * Convenience class to collect the click listeners together.
     *
     *
     * In a larger app it's better to split the listeners out or to use your favorite
     * library.
     */
    private inner class ClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            when (v.id) {
                R.id.button_previous -> mMediaBrowserHelper!!.transportControls.skipToPrevious()
                R.id.button_play -> if (mIsPlaying) {
                    mMediaBrowserHelper!!.transportControls.pause()
                } else {
                    mMediaBrowserHelper!!.transportControls.play()
                }
                R.id.button_next -> mMediaBrowserHelper!!.transportControls.skipToNext()
            }
        }
    }

    /**
     * Customize the connection to our [android.support.v4.media.MediaBrowserServiceCompat]
     * and implement our app specific desires.
     */
    private inner class MediaBrowserConnection (context: Context) : MediaBrowserHelper(context, MusicService::class.java) {

        override fun onConnected(mediaController: MediaControllerCompat) {
            mSeekBarAudio!!.setMediaController(mediaController)
        }

        override fun onChildrenLoaded(parentId: String,
                                       children: List<MediaBrowserCompat.MediaItem>) {
            super.onChildrenLoaded(parentId, children)

            val mediaController = getMediaController()

            // Queue up all media items for this simple sample.
            for (mediaItem in children) {
                mediaController.addQueueItem(mediaItem.description)
            }

            // Call prepare now so pressing play just works.
            mediaController.transportControls.prepare()
        }
    }

    /**
     * Implementation of the [MediaControllerCompat.Callback] methods we're interested in.
     *
     *
     * Here would also be where one could override
     * `onQueueChanged(List<MediaSessionCompat.QueueItem> queue)` to get informed when items
     * are added or removed from the queue. We don't do this here in order to keep the UI
     * simple.
     */
    private inner class MediaBrowserListener : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat?) {
            mIsPlaying = playbackState != null && playbackState.state == PlaybackStateCompat.STATE_PLAYING
            mMediaControlsImage!!.isPressed = mIsPlaying
        }

        override fun onMetadataChanged(mediaMetadata: MediaMetadataCompat?) {
            if (mediaMetadata == null) {
                return
            }
            mTitleTextView!!.text = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            mArtistTextView!!.text = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            mAlbumArt!!.setImageBitmap(MusicLibrary.getAlbumBitmap(
                    this@MainActivity,
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)))
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
        }

        override fun onQueueChanged(queue: List<MediaSessionCompat.QueueItem>?) {
            super.onQueueChanged(queue)
        }
    }
}
