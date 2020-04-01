/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.messaging.receiver;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PendingIntentConstants;

/**
 * Receiver that listens to SIM full action and shows SIM full notification.
 */
public final class SimFullReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        LogUtil.v(LogUtil.BUGLE_TAG, "SimFullReceiver.onReceive " + intent);
        final String action = intent.getAction();
        if (Telephony.Sms.Intents.SIM_FULL_ACTION.equals(action) && !OsUtil.isSecondaryUser()) {
            final Resources resources = context.getResources();
            final int subId =
                    intent.getIntExtra(
                            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                            ParticipantData.DEFAULT_SELF_SUB_ID);

            // Set the content intent
            PendingIntent destinationIntent =
                    UIIntents.get().getPendingIntentForSimCardMessageListActivity(context, subId);

            final Notification notification =
                    new NotificationCompat.Builder(context)
                            .setContentTitle(
                                    resources.getString(
                                            R.string.sim_card_full_title,
                                            subId == ParticipantData.DEFAULT_SELF_SUB_ID
                                                    ? ""
                                                    : String.valueOf(subId)))
                            .setContentText(resources.getString(R.string.sim_card_full_text))
                            .setTicker(
                                    resources.getString(R.string.sim_card_full_notification_ticker))
                            .setSmallIcon(R.drawable.ic_failed_light)
                            .setCategory(Notification.CATEGORY_STATUS)
                            .setAutoCancel(true)
                            .setContentIntent(destinationIntent)
                            .build();

            final NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(context);

            notificationManager.notify(
                    getNotificationTag(subId),
                    PendingIntentConstants.SIM_CARD_FULL_NOTIFICATION_ID,
                    notification);
        }
    }

    private static String getNotificationTag(final int subId) {
        return Factory.get().getApplicationContext().getPackageName() + ":simcardfull" + subId;
    }
}
