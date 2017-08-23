package com.google.android.exoplayer2.demo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by y.naito on 2017/08/23.
 */

public class NotificationService extends Service {
    private String TAG = NotificationService.class.getSimpleName();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent,flags,startId);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onStartCommand");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onStartCommand");
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved");
        super.onTaskRemoved(rootIntent);
    }
}
