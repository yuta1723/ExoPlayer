package com.google.android.exoplayer2.demo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.os.Build;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;

/**
 * Created by y.naito on 2017/07/19.
 */

class AudioNoisyReceiver extends BroadcastReceiver {
    private String TAG = AudioNoisyReceiver.class.getSimpleName();
    private SimpleExoPlayer player = null;
    private Context mContext;

    private static int NOTIFICATION_ID = 10000;
    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
            Log.d(TAG,"ヘッドホンが抜けた(ACTION_HEADSET_PLUG)");
        } else if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            Log.d(TAG, "ヘッドホンが抜けた(ACTION_AUDIO_BECOMING_NOISY)");
            DemoApplication demoApplication = (DemoApplication) context.getApplicationContext();
            player = demoApplication.getPlayerInstance();
            if (player == null) {
                //player == nullの時は、まだonDestroyまで遷移してない時なので
                context.startActivity(createPlayerActivityIntent(context, PlayerUtil.ACTION_PAUSE_INTENT));
                return;
            }
            player.setPlayWhenReady(false);
            pauseplayer();
            createNotification();
        }
    }

    public void pauseplayer() {
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(false);
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
        Notification.Builder builder = new Notification.Builder(mContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(Color.RED);
        }
        builder.setSmallIcon(R.mipmap.ic_launcher);
        Bitmap bmp1 = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bigbuckbunny);
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
            MediaSession mediaSession = new MediaSession(mContext.getApplicationContext(), "naito");
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(1));
        }
        NotificationManager manager = (NotificationManager) mContext.getSystemService(mContext.getApplicationContext().NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());//todo generate random notification Id
    }

    private PendingIntent getPendingIntentWithBroadcast(String action) {
        return PendingIntent.getBroadcast(mContext.getApplicationContext(), 0, new Intent(action), 0);
    }

    private Intent createPlayerActivityIntent(Context context, String action) {
        return new Intent(context,PlayerActivity.class).setAction(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}