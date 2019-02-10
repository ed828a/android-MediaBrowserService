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

package com.example.android.mediasession.service

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

import com.example.android.mediasession.service.contentcatalogs.MusicLibrary
import com.example.android.mediasession.service.notifications.MediaNotificationManager
import com.example.android.mediasession.service.players.MediaPlayerAdapter

import java.util.ArrayList

class MusicService : MediaBrowserServiceCompat() {

    private var mSession: MediaSessionCompat? = null
    private var mPlayback: PlayerAdapter? = null
    private var mMediaNotificationManager: MediaNotificationManager? = null
    private var mCallback: MediaSessionCallback? = null
    private var mServiceInStartedState: Boolean = false

    override fun onCreate() {
        super.onCreate()

        // Create a new MediaSession.
        mSession = MediaSessionCompat(this, "MusicService")
        mCallback = MediaSessionCallback()
        mSession!!.setCallback(mCallback)
        mSession!!.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        sessionToken = mSession!!.sessionToken

        mMediaNotificationManager = MediaNotificationManager(this)

        mPlayback = MediaPlayerAdapter(this, MediaPlayerListener())
        Log.d(TAG, "onCreate: MusicService creating MediaSession, and MediaNotificationManager")
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        mMediaNotificationManager!!.onDestroy()
        mPlayback!!.stop()
        mSession!!.release()
        Log.d(TAG, "onDestroy: MediaPlayerAdapter stopped, and MediaSession released")
    }

    override fun onGetRoot(clientPackageName: String,
                           clientUid: Int,
                           rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        return MediaBrowserServiceCompat.BrowserRoot(MusicLibrary.root, null)
    }

    override fun onLoadChildren(
            parentMediaId: String,
            result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(MusicLibrary.mediaItems)
    }

    // MediaSession Callback: Transport Controls -> MediaPlayerAdapter
    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        private val mPlaylist = ArrayList<MediaSessionCompat.QueueItem>()
        private var mQueueIndex = -1
        private var mPreparedMedia: MediaMetadataCompat? = null

        private val isReadyToPlay: Boolean
            get() = !mPlaylist.isEmpty()

        override fun onAddQueueItem(description: MediaDescriptionCompat?) {
            mPlaylist.add(MediaSessionCompat.QueueItem(description!!, description.hashCode().toLong()))
            mQueueIndex = if (mQueueIndex == -1) 0 else mQueueIndex
            mSession!!.setQueue(mPlaylist)
        }

        override fun onRemoveQueueItem(description: MediaDescriptionCompat?) {
            mPlaylist.remove(MediaSessionCompat.QueueItem(description!!, description.hashCode().toLong()))
            mQueueIndex = if (mPlaylist.isEmpty()) -1 else mQueueIndex
            mSession!!.setQueue(mPlaylist)
        }

        override fun onPrepare() {
            if (mQueueIndex < 0 && mPlaylist.isEmpty()) {
                // Nothing to play.
                return
            }

            val mediaId = mPlaylist[mQueueIndex].description.mediaId
            mPreparedMedia = MusicLibrary.getMetadata(this@MusicService, mediaId!!)
            mSession!!.setMetadata(mPreparedMedia)

            if (!mSession!!.isActive) {
                mSession!!.isActive = true
            }
        }

        override fun onPlay() {
            if (!isReadyToPlay) {
                // Nothing to play.
                return
            }

            if (mPreparedMedia == null) {
                onPrepare()
            }

            mPlayback!!.playFromMedia(mPreparedMedia!!)
            Log.d(TAG, "onPlayFromMediaId: MediaSession active")
        }

        override fun onPause() {
            mPlayback!!.pause()
        }

        override fun onStop() {
            mPlayback!!.stop()
            mSession!!.isActive = false
        }

        override fun onSkipToNext() {
            mQueueIndex = ++mQueueIndex % mPlaylist.size
            mPreparedMedia = null
            onPlay()
        }

        override fun onSkipToPrevious() {
            mQueueIndex = if (mQueueIndex > 0) mQueueIndex - 1 else mPlaylist.size - 1
            mPreparedMedia = null
            onPlay()
        }

        override fun onSeekTo(pos: Long) {
            mPlayback!!.seekTo(pos)
        }
    }

    // MediaPlayerAdapter Callback: MediaPlayerAdapter state -> MusicService.
    inner class MediaPlayerListener internal constructor() : PlaybackInfoListener() {

        private val mServiceManager: ServiceManager

        init {
            mServiceManager = ServiceManager()
        }

        override fun onPlaybackStateChange(state: PlaybackStateCompat) {
            // Report the state to the MediaSession.
            mSession!!.setPlaybackState(state)

            // Manage the started state of this service.
            when (state.state) {
                PlaybackStateCompat.STATE_PLAYING -> mServiceManager.moveServiceToStartedState(state)
                PlaybackStateCompat.STATE_PAUSED -> mServiceManager.updateNotificationForPause(state)
                PlaybackStateCompat.STATE_STOPPED -> mServiceManager.moveServiceOutOfStartedState(state)
            }
        }

        internal inner class ServiceManager {

            fun moveServiceToStartedState(state: PlaybackStateCompat) {
                val notification = mMediaNotificationManager!!.getNotification(
                        mPlayback!!.getCurrentMedia()!!, state, sessionToken!!)

                if (!mServiceInStartedState) {
                    ContextCompat.startForegroundService(
                            this@MusicService,
                            Intent(this@MusicService, MusicService::class.java))
                    mServiceInStartedState = true
                }

                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
            }

            fun updateNotificationForPause(state: PlaybackStateCompat) {
                stopForeground(false)
                val notification = mMediaNotificationManager!!.getNotification(
                        mPlayback!!.getCurrentMedia()!!, state, sessionToken!!)
                mMediaNotificationManager!!.notificationManager
                        .notify(MediaNotificationManager.NOTIFICATION_ID, notification)
            }

            fun moveServiceOutOfStartedState(state: PlaybackStateCompat) {
                stopForeground(true)
                stopSelf()
                mServiceInStartedState = false
            }
        }

    }

    companion object {

        private val TAG = MusicService::class.java.simpleName
    }

}