package com.android.messaging.trampoline;

// TODO(sansid): This is the only thing that pokes into the messaging app, but with a library it can bundle the resource too. gerrit build setup just isn;t great
import com.android.messaging.R;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Telephony;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;


public final class TrampolineManager {
    private static final String MESSAGES_PACKAGE = "com.google.android.apps.messaging";

    public TrampolineManager() {
    }

    public static boolean trampoline(AppCompatActivity activity) {
        android.util.Log.i("Trampoline", "Running....");

//        // if we are not the default SMS app, there's not a lot we can do, so ignore for now.
//        if (!TextUtils.equals(activity.getPackageName(), Telephony.Sms.getDefaultSmsPackage(activity))) {
//            android.util.Log.i("Trampoline", "OEM messages app is not the default, bailing out");
//            return false;
//        }

        // At this point we are the default, so if AM is installed, we disable ourselves and redirect
        // to it. (probably because we may have missed disabling ourselves earlier)?
        if (attemptTransitionToMessages(activity)) {
            launchMessages(activity);
            android.util.Log.i("Trampoline", "trampolining to Messages on app launch ...");
            return true;
        }

        // Show a promo here.
        AlertDialog alertDialog = new AlertDialog.Builder(activity)
            .setIcon(R.drawable.ic_messages)
            .setTitle("Upgrade your Messaging App")
            .setMessage("Upgrade your device's carrier messaging (SMS/MMS) app with Messages from Google to share higher quality photos, get spam protection, chat over Wifi and data, and more.\n\n\nInstall now to set Messages as the default SMS app. Your original SMS app will always launch Messages unless you uninstall Messages.\nLearn More")
            .setPositiveButton(
                "Install", new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        launchPlayStore(activity);
                        dialog.dismiss();
                    }
                }).setNegativeButton("Not now", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).show();

        return false;
    }

    public static boolean isMessagesInstalled(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(MESSAGES_PACKAGE, /* flags= */ 0) != null;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public static void launchMessages(Context context) {
        context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(MESSAGES_PACKAGE));
    }

    public static boolean attemptTransitionToMessages(Context context) {
        android.util.Log.i("Trampoline", "attemptTransitionToMessages");

        PackageManager pm = context.getPackageManager();
        if (isMessagesInstalled(context)) {
            for (ResolveInfo receiver : pm.queryBroadcastReceivers(new Intent("android.provider.Telephony.SMS_DELIVER").setPackage(context.getPackageName()), PackageManager.GET_META_DATA)) {
                ComponentName cn = new ComponentName(receiver.activityInfo.packageName, receiver.activityInfo.name);
                if (pm.getComponentEnabledSetting(cn) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(
                            cn,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                    android.util.Log.d("Trampoline", "Disabled OEM messaging: " + receiver.activityInfo.name);
                }
            }
            return true;
        } else {
            for (ResolveInfo receiver : pm.queryBroadcastReceivers(new Intent("android.provider.Telephony.SMS_DELIVER").setPackage(context.getPackageName()), PackageManager.GET_META_DATA|PackageManager.GET_DISABLED_COMPONENTS)) {
                ComponentName cn = new ComponentName(receiver.activityInfo.packageName, receiver.activityInfo.name);
                if (pm.getComponentEnabledSetting(cn) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                            new ComponentName(receiver.activityInfo.packageName, receiver.activityInfo.name),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    android.util.Log.d("Trampoline", "Enabled OEM messaging: " + receiver.activityInfo.name);
                }
            }
            return false;
        }
    }

    public static void launchPlayStore(AppCompatActivity activity) {
        Intent playStoreIntent =
                new Intent(Intent.ACTION_VIEW)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        .setData(Uri.parse("market://details?id=" + MESSAGES_PACKAGE + "&referrer=trampoline"))
                        .putExtra("callerId", activity.getPackageName())
                        .putExtra("overlay", true)
                        .setPackage("com.android.vending");
        activity.startActivityForResult(playStoreIntent, 0);
    }
}
