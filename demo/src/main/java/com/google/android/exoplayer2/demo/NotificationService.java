package com.google.android.exoplayer2.demo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by y.naito on 2017/08/23.
 */

public class NotificationService extends Service {
    private String TAG = NotificationService.class.getSimpleName();

    private String TITLE_NOTIFICATION = "BigBuckBunny";
    private String TEXT_NOIFICATION = "で再生しています。";

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
        Log.d(TAG, "onStartCommand Flag : " + flags + " intent : " + intent.getAction());
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

    private void createControlerNotification(boolean isPlaying) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher);
        Bitmap bmp1 = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
        builder.setLargeIcon(bmp1);
        builder.setContentTitle(TITLE_NOTIFICATION);
        builder.setContentText(getApplicationName() + TEXT_NOIFICATION);
        builder.setContentIntent(getPendingIntentWithActivities());
        builder.setShowWhen(false);
        builder.setDeleteIntent(getPendingIntentWithBroadcast(PlayerUtil.ACTION_STOP_PLAYER));
        if (isPlaying) {
            builder.addAction(R.drawable.exo_controls_pause, "一時停止", getPendingIntentWithBroadcast(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT));
        } else {
            builder.addAction(R.drawable.exo_controls_play, "再生", getPendingIntentWithBroadcast(PlayerUtil.ACTION_TOGGLE_PLAY_PAUSE_INTENT));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSessionCompat mediaSession = new MediaSessionCompat(getApplicationContext(), "naito");
            builder.setStyle(new NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0));
        }
        Notification notification = builder.build();
        NotificationManagerCompat manager = NotificationManagerCompat.from(getApplicationContext());
        if (isPlaying) {
            notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
            manager.notify(NOTIFICATION_ID, notification);
            //memo startForegroundを行うと、通知の削除ができなくなる。
            //そのため、startForeground/stopForegroundをとりあえず使用しない形で実装
//            startForeground(NOTIFICATION_ID, notification);
        } else {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent getPendingIntentWithBroadcast(String action) {
        return PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(action), 0);
    }

    private PendingIntent getPendingIntentWithActivities() {
        return PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), PlayerActivity.class), 0);
    }

    private void goneNotification() {
//        stopForeground(true);
        NotificationManager manager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }

    private String getApplicationName() {
        String appName = "";
        int id = getResources().getIdentifier("application_name", "string", getPackageName());
        if (id != 0) {
            appName = getResources().getString(id);
        }
        return appName;
    }
}
