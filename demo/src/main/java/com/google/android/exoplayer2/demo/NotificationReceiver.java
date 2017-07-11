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
            case PlayerUtil.ACTION_RESTART_ACTIVITY:
                context.startActivity(createPlayerActivityIntent(context, action));
                break;
            case PlayerUtil.ACTION_PAUSE_INTENT:
                context.startService(createPlayerActivityIntent(context, action));
                break;
            case PlayerUtil.ACTION_SEEK_TO_FOWARD_INTENT:
                context.startService(createPlayerActivityIntent(context, action));
                break;
            case PlayerUtil.ACTION_SEEK_TO_PREVIOUS_INTENT:
                context.startService(createPlayerActivityIntent(context, action));
                break;
        }
    }

    private Intent createPlayerServiceIntent(Context context, String action) {
        return new Intent(context, PlayerService.class).setAction(action);
    }

    private Intent createPlayerActivityIntent(Context context, String action) {
        return new Intent(context,PlayerActivity.class).setAction(action);
    }
}
