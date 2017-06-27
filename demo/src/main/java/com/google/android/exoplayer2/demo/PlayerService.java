package com.google.android.exoplayer2.demo;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;

/**
 * Created by y.naito on 2017/06/21.
 */

public class PlayerService extends Service {
    private String TAG = PlayerService.class.getSimpleName();

    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    Handler mainHandler;
    private EventLogger eventLogger;
    private DataSource.Factory mediaDataSourceFactory;
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private Uri contentUrl = Uri.parse("http://54.248.249.96/maruyama/short.mp4");
    private String contentExtention = "mp4";
    private long resumePosition;
    private boolean haveResumePosition = false;

    private int FLAG_PAUSE_INTENT = 100;
    private String ACTION_PAUSE_INTENT = "action_pause";

    public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
    public static final String EXTENSION_EXTRA = "extension";

    public static final String ACTION_VIEW_LIST =
            "com.google.android.exoplayer.demo.action.VIEW_LIST";
    public static final String URI_LIST_EXTRA = "uri_list";

    public static final String EXTENSION_LIST_EXTRA = "extension_list";


    public class LocalBinder extends Binder {
        //Serviceの取得
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        Log.d(TAG, "flags : " + flags + " startId " + startId + " intent " + intent.toString());
        if (intent.getAction().equals(ACTION_PAUSE_INTENT)) {
            if (isPlaying()) {
                player.setPlayWhenReady(false);
            } else {
                player.setPlayWhenReady(true);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        mediaDataSourceFactory = buildDataSourceFactory(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        updateResumePositionForIntent(intent);
        createPlayerInstance(intent);
        getAudioFocus();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        if (player != null) {
            player.release();
            player = null;
        }
        return false;
//        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
                : uri.getLastPathSegment());
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
            case C.TYPE_DASH:
                return new DashMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
            case C.TYPE_HLS:
                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, eventLogger);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                        mainHandler, eventLogger);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return ((DemoApplication) getApplication())
                .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    private void createPlayerInstance(Intent intent) {
        mainHandler = new Handler();
        eventLogger = new EventLogger(trackSelector);
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
        trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

// 2. Create the player
        player =
                ExoPlayerFactory.newSimpleInstance(getBaseContext(), trackSelector, new DefaultLoadControl());

//        MediaSource mediaSource = buildMediaSource(contentUrl,contentExtention);


        String action = intent.getAction();
        Uri[] uris;
        String[] extensions;
        if (ACTION_VIEW.equals(action)) {
            uris = new Uri[]{intent.getData()};
            extensions = new String[]{intent.getStringExtra(EXTENSION_EXTRA)};
        } else if (ACTION_VIEW_LIST.equals(action)) {
            String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);
            uris = new Uri[uriStrings.length];
            for (int i = 0; i < uriStrings.length; i++) {
                uris[i] = Uri.parse(uriStrings[i]);
            }
            extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA);
            if (extensions == null) {
                extensions = new String[uriStrings.length];
            }
        } else {
//                showToast(getString(R.string.unexpected_intent_action, action));
            return;
        }

        MediaSource[] mediaSources = new MediaSource[uris.length];
        for (int i = 0; i < uris.length; i++) {
            mediaSources[i] = buildMediaSource(uris[i], extensions[i]);
        }
        MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
                : new ConcatenatingMediaSource(mediaSources);


        boolean haveResumePosition = resumePosition != 0;

        if (haveResumePosition) {
            player.seekTo(0, resumePosition);
        }
        player.prepare(mediaSource, false, false);
        player.setPlayWhenReady(true);

//        Intent notificationIntent = new Intent(this, PlayerActivity.class);
        Intent notificationIntent = intent.setClass(this,PlayerActivity.class);
        PendingIntent conntentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Intent notificationIntent2 = new Intent(this, PlayerService.class);
        notificationIntent2.setAction(ACTION_PAUSE_INTENT);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, FLAG_PAUSE_INTENT, notificationIntent2, 0);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("TITLE iS XX");
        builder.setContentText("Text is XX");
        builder.setContentIntent(conntentIntent);
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//            Notification.MediaStyle style = new Notification.MediaStyle();
//            builder.setStyle(style);
//        }
        builder.addAction(R.drawable.exo_controls_pause, "Pause", pausePendingIntent);  // #1
        NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        manager.notify(1, builder.build());


//        Intent intent = new Intent(getApplicationContext(), PlayerService.class);
//        PendingIntent pausePendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, 0);

//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//            Notification notification = new Notification.Builder(this)
//                    // Show controls on lock screen even when user hides sensitive content.
//                    .setVisibility(Notification.VISIBILITY_PUBLIC)
//                    .setSmallIcon(R.mipmap.ic_launcher)
//                    // Add media control buttons that invoke intents in your media service
//                    //                .addAction(R.drawable.ic_prev, "Previous", prevPendingIntent) // #0
//                    .addAction(R.drawable.exo_controls_pause, "Pause", pausePendingIntent)  // #1
//                    //                .addAction(R.drawable.ic_next, "Next", nextPendingIntent)     // #2
//                    // Apply the media style template
//
//                    .build();
//
//            NotificationManager mNotificationManager =
//                    (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
//// mId allows you to update the notification later on.
//            mNotificationManager.notify(15245, notification);
//        }
    }

    private void updateResumePositionForIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        resumePosition = intent.getLongExtra(PlayerActivity.CURRENT_POSITION_FOR_RESUME, 0);
        Log.d(TAG, "resumePosition : " + resumePosition);
    }

    public boolean isPlaying() {
        //Log.enter(TAG, "isPlaying", "");
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

    private void getAudioFocus() {
        AudioManager am = (AudioManager) getSystemService(this.AUDIO_SERVICE);
        am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int i) {
                Log.d(TAG, "onAudioFocusChange");
                player.setPlayWhenReady(false);
            }
        }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

    }
}
