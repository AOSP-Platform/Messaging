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
package com.android.messaging.ui.conversation;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;

import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.SimCardMessageListItemData;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays an active SIM card list to copy selected SMS into chosen SIM card by a user.
 */
public class CopySmsToSimSelectorDialog {
    private final Context mContext;
    private final String mMessageUri;
    private final SimSelectorAdapter mAdapter;
    private AlertDialog mDialog;

    /** Shows a new SIM card selector dialog. */
    public static void showDialog(
            final Context context,
            final List<SubscriptionListEntry> list,
            final String messageUri) {
        new CopySmsToSimSelectorDialog(context, list, messageUri).show();
    }

    private CopySmsToSimSelectorDialog(
            final Context context,
            final List<SubscriptionListEntry> list,
            final String messageUri) {
        mContext = context;
        mMessageUri = messageUri;
        mAdapter = new SimSelectorAdapter(context, list);
    }

    private void show() {
        Assert.isNull(mDialog);

        AlertDialog.Builder dialogBuilder =
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.copy_sim_card_selector_dialog_title)
                        .setNegativeButton(android.R.string.cancel, null);

        if (mAdapter.isEmpty()) {
            dialogBuilder.setMessage(R.string.copy_sim_card_selector_no_sim_message).show();
        } else {
            mDialog = dialogBuilder.setAdapter(mAdapter, null).show();
            mDialog.getListView()
                    .setOnItemClickListener(
                            new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(
                                        AdapterView<?> parent, View view, int position, long id) {
                                    final SubscriptionListEntry item =
                                            (SubscriptionListEntry) mAdapter.getItem(position);
                                    if (item != null) {
                                        new AsyncCopySmsToSimTask(
                                                        mContext,
                                                        Uri.parse(mMessageUri),
                                                        item.selfParticipantId)
                                                .executeOnThreadPool(null, null, null);
                                    }
                                    mDialog.dismiss();
                                }
                            });
        }
    }

    /**
     * An adapter that takes a list of SubscriptionListEntry and displays them as a list of
     * available SIMs in the SIM selector.
     */
    private class SimSelectorAdapter extends ArrayAdapter<SubscriptionListEntry> {
        public SimSelectorAdapter(final Context context, final List<SubscriptionListEntry> list) {
            super(context, R.layout.copy_sms_to_sim_selector_item_view,
                    new ArrayList<SubscriptionListEntry>());
            addAll(list);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            CopySmsToSimSelectorItemView itemView;
            if (convertView != null && convertView instanceof CopySmsToSimSelectorItemView) {
                itemView = (CopySmsToSimSelectorItemView) convertView;
            } else {
                final LayoutInflater inflater =
                        (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                itemView = (CopySmsToSimSelectorItemView) inflater.inflate(
                        R.layout.copy_sms_to_sim_selector_item_view, parent, false);
            }
            itemView.bind(getItem(position));
            return itemView;
        }
    }

    private static class AsyncCopySmsToSimTask extends SafeAsyncTask<Void, Void, Uri> {
        private final Context mContext;
        private final Uri mMessageUri;
        private final String mSelfId;

        private static final String[] PROJECTION =
            new String[] {
                Sms.ADDRESS,
                Sms.BODY,
                Sms.DATE,
                Sms.TYPE
            };

        private static final int INDEX_ADDRESS = 0;
        private static final int INDEX_BODY = 1;
        private static final int INDEX_DATE = 2;
        private static final int INDEX_TYPE = 3;

        public AsyncCopySmsToSimTask(
                final Context context, final Uri messageUri, final String selfId) {
            mContext = context;
            mMessageUri = messageUri;
            mSelfId = selfId;
        }

        @Override
        protected Uri doInBackgroundTimed(final Void... params) {
            final ContentResolver cr = mContext.getContentResolver();
            final DatabaseWrapper db = DataModel.get().getDatabase();
            final int subId = BugleDatabaseOperations.getSelfSubscriptionId(db, mSelfId);

            Uri ret = null;
            try (Cursor cursor = cr.query(mMessageUri, PROJECTION, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    ContentValues messageValues = new ContentValues();
                    messageValues.put(Sms.ADDRESS, cursor.getString(INDEX_ADDRESS));
                    final SmsManager smsManager = PhoneUtils.get(subId).getSmsManager();
                    final String messageText = cursor.getString(INDEX_BODY);
                    // Only single-part message can be written.
                    if (smsManager.divideMessage(messageText).size() != 1) {
                        LogUtil.e(LogUtil.BUGLE_TAG, "Only single-part message is allowed"
                                + ", body=" + messageText);
                        return null;
                    }
                    messageValues.put(Sms.BODY, messageText);
                    messageValues.put(Sms.DATE, cursor.getLong(INDEX_DATE));
                    messageValues.put(Sms.TYPE, cursor.getInt(INDEX_TYPE));

                    Uri uri = subId == ParticipantData.DEFAULT_SELF_SUB_ID
                                    ? SimCardMessageListItemData.ICC_URI
                                    : ContentUris.withAppendedId(
                                            SimCardMessageListItemData.ICC_SUBID_URI, subId);

                    if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                        LogUtil.d(LogUtil.BUGLE_TAG, "Copy SMS into ICC"
                                + ", uri=" + uri + ", message={" + messageValues + "}");
                    }
                    ret = cr.insert(uri, messageValues);
                    if (ret == null) {
                        LogUtil.e(LogUtil.BUGLE_TAG, "Failed to copy SMS into ICC"
                                + ", uri=" + uri + ", message={" + messageValues + "}");
                    }
                } else {
                    LogUtil.e(LogUtil.BUGLE_TAG, "Failed to read SMS from telephony"
                            + ", uri=" + mMessageUri);
                }
            } catch (IllegalArgumentException e) {
                LogUtil.e(LogUtil.BUGLE_TAG, "Copying SMS into ICC failed: " + e);
            }
            return ret;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            UiUtils.showToastAtBottom(
                    uri == null
                            ? mContext.getString(R.string.fail_copy_sms_to_sim_message)
                            : mContext.getString(R.string.success_copy_sms_to_sim_message));
        }
    }
}
