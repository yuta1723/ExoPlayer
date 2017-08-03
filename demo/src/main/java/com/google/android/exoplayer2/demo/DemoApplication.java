/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.os.Build;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

/**
 * Placeholder application to facilitate overriding Application methods for debugging and testing.
 */
public class DemoApplication extends Application {
    private String TAG = DemoApplication.class.getSimpleName();
    private static int NOTIFICATION_ID = 10000;

    private long SEEK_TO_PREVIOUS_DEFAULT_VALUE = 1500;
    private long SEEK_TO_FOWARDS_DEFAULT_VALUE = 1500;

    @Override
    public void onTerminate() {
        Log.d(TAG,"");
        super.onTerminate();

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    protected String userAgent;
    private SimpleExoPlayer player = null;
    private AudioNoisyReceiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
        Log.d(TAG,"onCreate");
    }

    public DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultDataSourceFactory(this, bandwidthMeter,
                buildHttpDataSourceFactory(bandwidthMeter));
    }

    public HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultHttpDataSourceFactory(userAgent, bandwidthMeter);
    }

    public boolean useExtensionRenderers() {
        return BuildConfig.FLAVOR.equals("withExtensions");
    }

    public void setPlayerInstance(SimpleExoPlayer player) {
        this.player = player;
    }

    public SimpleExoPlayer getPlayerInstance() {
        return this.player;
    }

    public void registAudioBroadcastReceiver() {
        receiver = new AudioNoisyReceiver();
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        registerReceiver(receiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    public void registPlayerControllerBroadcastReceiver(IntentFilter filter) {
        receiver = new AudioNoisyReceiver();
        registerReceiver(mIntentReceiver, filter);
    }

    public void pauseplayer() {
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(false);
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive : intent " + intent + " context : " + context);
            checkIntent(intent);
        }
    };

    private void checkIntent(Intent intent) {
        if (intent == null || player == null) {
            return;
        }
        if (intent.getAction().equals(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT)) {
            if (isPlaying()) {
                player.setPlayWhenReady(false);
            } else {
                player.setPlayWhenReady(true);
//                createAudioFocus();
            }
            createNotification();
        } else if (intent.getAction().equals(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT)) {
            player.seekTo(player.getCurrentPosition() - SEEK_TO_PREVIOUS_DEFAULT_VALUE);
        } else if (intent.getAction().equals(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT)) {
            player.seekTo(player.getCurrentPosition() + SEEK_TO_FOWARDS_DEFAULT_VALUE);
        } else if (intent.getAction().equals(PlayerUtil.ACTION_RESTART_ACTIVITY)) {
            Log.d(TAG, "back to playback into activity");
            Intent i = new Intent(this, PlayerActivity.class);
            i.setAction(PlayerUtil.ACTION_RESTART_ACTIVITY);
            startActivity(i);
        } else if (intent.getAction().equals(PlayerUtil.ACTION_DELETE_PLAYER)) {
            releasePlayer();

        }
    }

    public boolean isPlaying() {
        if (player == null) {
            return false;
        }
        if (player.getPlayWhenReady()) {
            if (player.getPlaybackState() == ExoPlayer.STATE_READY || player.getPlaybackState() == ExoPlayer.STATE_BUFFERING) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void createNotification() {
        if (player == null) {
            return;
        }
        createControlerNotification(isPlaying());
    }

    private void createControlerNotification(boolean isplay) {
        Notification.Builder builder = new Notification.Builder(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(Color.RED);
        }
        builder.setSmallIcon(R.mipmap.ic_launcher);
        Bitmap bmp1 = BitmapFactory.decodeResource(getResources(), R.drawable.bigbuckbunny);
        builder.setLargeIcon(bmp1);
        builder.setContentTitle("TITLE iS XX");
        builder.setContentText("Text is XX");
        builder.setContentIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_RESTART_ACTIVITY));
        builder.setDeleteIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_DELETE_PLAYER));
        builder.addAction(R.drawable.exo_controls_previous, "<<", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT));
        if (isplay) {
            builder.addAction(R.drawable.exo_controls_pause, "Pause", getPendingIntentWithBroadcast(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT));
        } else {
            builder.addAction(R.drawable.exo_controls_play, "Play", getPendingIntentWithBroadcast(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT));
        }
        builder.addAction(R.drawable.exo_controls_fastforward, ">>", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSession mediaSession = new MediaSession(getApplicationContext(), "naito");
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(1));
        }
        NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());//todo generate random notification Id
    }

    private PendingIntent getPendingIntentWithBroadcast(String action) {
        return PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(action), 0);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void createAudioFocus() {
        AudioManager am = (AudioManager) getSystemService(this.AUDIO_SERVICE);
        am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
            boolean isTransient = false;

            @Override
            public void onAudioFocusChange(int focusChange) {
                Log.d(TAG, "onAudioFocusChange");
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.d(TAG, "AUDIOFOCUS_LOSS");
                        audioFocusLoss();
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.d(TAG, "AUDIOFOCUS_GAIN");
                        audioFocusGain();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        audioFocusLossTransmit();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        audioFocusLossTransmitCanDuck();
                        break;
                }
            }

            private void audioFocusLoss() {
                if (player == null) {
                    return;
                }
                player.setPlayWhenReady(false);
                createNotification();
            }

            private void audioFocusGain() {
                if (player == null) {
                    return;
                }
                if (isTransient) {
                    Log.d(TAG, "play stop");
                    player.setPlayWhenReady(true);
                    createNotification();
                }

            }

            private void audioFocusLossTransmit() {
                isTransient = true;
                if (player == null) {
                    return;
                }
                if (isPlaying()) {
                    Log.d(TAG, "play stop");
                    player.setPlayWhenReady(false);
                    createNotification();
                }
            }

            private void audioFocusLossTransmitCanDuck() {

            }
        }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void goneNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }
}


