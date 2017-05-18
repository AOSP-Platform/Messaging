package com.android.messaging.trampoline;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public final class PackageAddedOrRemovedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        android.util.Log.i("Trampoline", "received PackageAddedOrRemovedReceiver broadcast");
        if (TrampolineManager.attemptTransitionToMessages(context)) {
            // Ideally we launch AM here only if the OEM messaging app is running to seamlessly
            // switch
        }
    }
}
