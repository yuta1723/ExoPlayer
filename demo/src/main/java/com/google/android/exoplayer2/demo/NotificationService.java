package com.google.android.exoplayer2.demo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by y.naito on 2017/08/23.
 */

public class NotificationService extends Service {
    private String TAG = NotificationService.class.getSimpleName();

    private static int NOTIFICATION_ID = 10000;
    static final int MSG_CHANGE_PLAY = 2;
    static final int MSG_CHANGE_PAUSE = 3;
    static final int MSG_REMOVE_NOTIFICATION = 4;



    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CHANGE_PLAY:
                    Toast.makeText(getApplicationContext(), "MSG_CHANGE_PLAY!", Toast.LENGTH_SHORT).show();
                    createControlerNotification(false);
                    Log.d(TAG,"MSG_CHANGE_PLAY");
                    break;
                case MSG_CHANGE_PAUSE:
                    Toast.makeText(getApplicationContext(), "MSG_CHANGE_PAUSE!", Toast.LENGTH_SHORT).show();
                    createControlerNotification(true);
                    Log.d(TAG,"MSG_CHANGE_PAUSE");
                    break;
                case MSG_REMOVE_NOTIFICATION:
                    Toast.makeText(getApplicationContext(), "MSG_REMOVE_NOTIFICATION!", Toast.LENGTH_SHORT).show();
                    goneNotification();
//                    stopSelf();
                    Log.d(TAG,"MSG_REMOVE_NOTIFICATION");
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mMessenger.getBinder();
    }

    @Override
    public void onTrimMemory(int level) {
        Log.d(TAG, "onTrimMemory level : " + level);
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, "onStartConLowMemoryommand");
        goneNotification();
        super.onLowMemory();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent == null) {
//            return super.onStartCommand(intent, flags, startId);
//        }
        Log.d(TAG, "onStartCommand Flag : " + flags + " intent : " + intent.getAction());
//        if (intent.getAction().equals(PlayerUtil.ACTION_CREATE_NOTIFICATION)) {
//            boolean isplaying = intent.getBooleanExtra("isplaying", false);
//            createControlerNotification(isplaying);
//        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        goneNotification();//onDestroyが必ず呼ばれるの?
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved");
        super.onTaskRemoved(rootIntent);
        goneNotification();
        stopSelf();
    }

    private void createControlerNotification(boolean isplay) {
        Notification.Builder builder = new Notification.Builder(this);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            builder.setColor(Color.RED);
//        }
        builder.setSmallIcon(R.mipmap.ic_launcher);
        Bitmap bmp1 = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        builder.setLargeIcon(bmp1);
        builder.setContentTitle("TITLE iS XX");
//        builder.setContentText("TEXT is XX");
        builder.setContentIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_RESTART_ACTIVITY));
//        builder.setDeleteIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_DELETE_PLAYER));
//        builder.addAction(R.drawable.exo_controls_rewind, "", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT));
        if (isplay) {
            builder.addAction(R.drawable.exo_controls_pause, "", getPendingIntentWithBroadcast(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT));
        } else {
            builder.addAction(R.drawable.exo_controls_play, "", getPendingIntentWithBroadcast(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT));
        }
        builder.addAction(R.mipmap.uliza_ic_clear_white_36dp, "", getPendingIntentWithBroadcast(PlayerUtil.ACTION_STOP_PLAYER));
//        builder.addAction(R.drawable.exo_controls_fastforward, "", getPendingIntentWithBroadcast(PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSession mediaSession = new MediaSession(getApplicationContext(), "naito");
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0,1));
        }
        NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        Notification notification = builder.build();
        notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
//        manager.notify(NOTIFICATION_ID, notification);//todo generate random notification Id
        startForeground(NOTIFICATION_ID, notification);
    }

    private PendingIntent getPendingIntentWithBroadcast(String action) {
        return PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(action), 0);
    }

    private void goneNotification() {
        stopForeground(true);
//        NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
//        manager.cancel(NOTIFICATION_ID);
    }
}
