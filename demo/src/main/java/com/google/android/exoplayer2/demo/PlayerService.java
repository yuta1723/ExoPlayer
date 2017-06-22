package com.google.android.exoplayer2.demo;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
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
        createPlayerInstance();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG,"onUnbind");
        if (player != null) {
            player.release();
            player = null;
        }
        return false;
//        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG,"onRebind");
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

    private void createPlayerInstance() {
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
        MediaSource mediaSource = new ExtractorMediaSource(contentUrl, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                mainHandler, eventLogger);

        boolean haveResumePosition = resumePosition != 0;

        if (haveResumePosition) {
            player.seekTo(0, resumePosition);
        }
        player.prepare(mediaSource, false, false);
        player.setPlayWhenReady(true);

    }

    private void updateResumePositionForIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        resumePosition = intent.getLongExtra(PlayerActivity.CURRENT_POSITION_FOR_RESUME,0);
        Log.d(TAG,"resumePosition : " + resumePosition);
    }
}
