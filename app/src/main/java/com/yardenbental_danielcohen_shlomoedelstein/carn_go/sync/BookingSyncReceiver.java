package com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BookingSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BookingSyncScheduler.requestImmediateSync(context, "alarm");
    }
}
