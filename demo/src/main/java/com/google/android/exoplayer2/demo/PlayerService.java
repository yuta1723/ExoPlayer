package com.google.android.exoplayer2.demo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by y.naito on 2017/06/21.
 */

public class PlayerService extends Service {
    private String TAG = PlayerService.class.getSimpleName();

    private SimpleExoPlayer player;
    Handler mainHandler;
    private long resumePosition;

    private long SEEK_TO_PREVIOUS_DEFAULT_VALUE = 1500;
    private long SEEK_TO_FOWARDS_DEFAULT_VALUE = 1500;

    private static int NOTIFICATION_ID = 10000;

    private DemoApplication demoApplication;


    public class LocalBinder extends Binder {
        //Serviceの取得
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

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

        demoApplication = (DemoApplication) getApplication();

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(PlayerUtil.ACTION_RESTART_ACTIVITY);
        commandFilter.addAction(PlayerUtil.ACTION_PAUSE_INTENT);
        commandFilter.addAction(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT);
        commandFilter.addAction(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT);

        registerReceiver(mIntentReceiver, commandFilter);

    }

    private void checkIntent(Intent intent) {
        if (intent == null || player == null) {
            return;
        }
        if (intent.getAction().equals(PlayerUtil.ACTION_PAUSE_INTENT)) {
            if (isPlaying()) {
                player.setPlayWhenReady(false);
                createPlayNotification();
            } else {
                player.setPlayWhenReady(true);
                createPauseNotification();
            }
        } else if (intent.getAction().equals(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT)) {
            player.seekTo(player.getCurrentPosition() - SEEK_TO_PREVIOUS_DEFAULT_VALUE);
        } else if (intent.getAction().equals(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT)) {
            player.seekTo(player.getCurrentPosition() + SEEK_TO_FOWARDS_DEFAULT_VALUE);
        } else if (intent.getAction().equals(PlayerUtil.ACTION_RESTART_ACTIVITY)) {
            intent.setAction(PlayerActivity.ACTION_VIEW);
            Log.d(TAG, "back to playback into activity");
            intent.setClass(this, PlayerActivity.class);//fixme 実装が適当すぎる。resumePosition及びurlをfieldで管理するべき
            long currentPosition = 0;
            currentPosition = player.getCurrentPosition();
            intent.putExtra(PlayerActivity.CURRENT_POSITION_FOR_RESUME, currentPosition);
            startActivity(intent);
            releasePlayerAndStopService();
        } else if (intent.getAction().equals(PlayerUtil.ACTION_DELETE_PLAYER)) {
            releasePlayerAndStopService();
        }
    }

    private void releasePlayerAndStopService() {
        setPlayerInstance();
        releasePlayer();
        stopSelf();
    }

    private void setPlayerInstance() {
        if (player == null) {
            return;
        }
        demoApplication.setPlayerInstance(player);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
        }
        player = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (intent == null || player == null) {
            return super.onStartCommand(intent, flags, startId);
        }
        Log.d(TAG, "flags : " + flags + " startId " + startId + " intent " + intent.toString());
        checkIntent(intent);
        return super.onStartCommand(intent, flags, startId);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        updateResumePositionForIntent(intent);
        getPlayerInstance();
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
        stopSelf();
        unregisterReceiver(mIntentReceiver);
//        return true;
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
        updateResumePositionForIntent(intent);
        getPlayerInstance();
        getAudioFocus();
    }

    private void getPlayerInstance() {
        player = demoApplication.getPlayerInstance();
        Log.d(TAG, "" + player.getCurrentPosition());
        mainHandler = new Handler();
        createPauseNotification();
    }

    private void createPauseNotification() {
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
        builder.addAction(R.drawable.exo_controls_previous, "<<", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT));
        builder.addAction(R.drawable.exo_controls_pause, "Pause", getPendingIntentWithBroadcast(PlayerUtil.ACTION_PAUSE_INTENT));
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

    private void createPlayNotification() {
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
        builder.addAction(R.drawable.exo_controls_play, "Play", getPendingIntentWithBroadcast(PlayerUtil.ACTION_PAUSE_INTENT));
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
                //本来はより細かな制御をかけるべき
                if (player == null) {
                    return;
                }
                if (i == AudioManager.AUDIOFOCUS_LOSS) {
                    if (isPlaying()) {
                        Log.d(TAG, "play stop");

                        player.setPlayWhenReady(false);
                    }
                    player.release();
                    player = null;
                } else {
                    Log.d(TAG, "type : " + i);
                    if (player == null) {
                        return;
                    }
                    if (isPlaying()) {
                        Log.d(TAG, "play stop");

                        player.setPlayWhenReady(false);
                    } else {

                        //他のstreamにfocasされた際に、再生を停止する処理は必須だが
                        //他のstreamからこのServiceにfocusされた際に再生を開始すべき?
                        Log.d(TAG, "play start");
                        player.setPlayWhenReady(true);
                    }
                }
            }
        }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    public static int getNotificationId() {
        return NOTIFICATION_ID;
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    private PendingIntent getPendingIntentWithBroadcast(String action) {
        return PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(action), 0);
    }


    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive : intent " + intent + " context : " + context);
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
        }
    };

}

//todo 通知が削除された際の処理。
//todo 通知が押下された際の処理。
//todo 一回backgroundに遷移したら2回目以降はbackground再生されない。

//todo seekボタンを追加 ( -15秒 , +15秒)
//todo 通知領域に表示されているplay/pauseを切り替え
//todo ボタンに変化
//todo plaerインスタンスをやりとりする処理を追加(playerインスタンスのやりとりを行わないと、live , dvrなどのresumeが行い辛くなる。)
//todo application class 内にplayerをsetする方法が失敗すると思ったが、違うのか?
