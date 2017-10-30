package com.google.android.exoplayer2.demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by y.naito on 2017/07/03.
 */

public class NotificationReceiver extends BroadcastReceiver {
    private String TAG = NotificationReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive intent : " + intent);
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        Log.d(TAG, "action " + action);
        switch (action) {
            case NotificationService.ACTION_TOGGLE_PLAY_PAUSE_INTENT:
                context.startActivity(createPlayerActivityIntent(context, action));
                break;
            case NotificationService.ACTION_STOP_PLAYER:
                context.startActivity(createPlayerActivityIntent(context, action));
                break;
        }
    }

    private Intent createPlayerActivityIntent(Context context, String action) {
        return new Intent(context, PlayerActivity.class).setAction(action);
    }
}
