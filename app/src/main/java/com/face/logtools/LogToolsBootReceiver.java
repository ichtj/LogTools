package com.face.logtools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LogToolsBootReceiver extends BroadcastReceiver {
    private static final String TAG=LogToolsBootReceiver.class.getSimpleName();
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d (TAG, "onReceive: "+intent.getAction ());
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(mainIntent);
    }
}
