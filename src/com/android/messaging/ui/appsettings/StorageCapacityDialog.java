/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.messaging.ui.appsettings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.telephony.SmsMessage;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.text.format.Formatter;
import android.text.TextUtils;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;

import com.android.messaging.R;
import com.android.messaging.sms.DatabaseMessages;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Displays Storage Capacity. */
public class StorageCapacityDialog {
    private final Context mContext;
    private ProgressDialog proDialog;
    private static String MMS_URL = "content://mms/";
    private static String SMS_URL = "content://sms/";
    public static final Uri ICC_URI = Uri.parse("content://sms/icc");
    public static final Uri ICC_SUB_URI = Uri.parse("content://sms/icc_sub");
    private static final String STORAGE_CAPACITY = "storage_capacity";
    private static final String STORAGE_NAME = "storage_name";
    private static final String TAG = "StorageCapacityDialog";

    /** Show progress dialog */
    public static void showDialog(final Context context, final int subId) {
        new StorageCapacityDialog(context, subId).show();
    }

    private StorageCapacityDialog(final Context context, final int subId) {
        mContext = context;
        ProgressBarAsyncTask asyncTask = new ProgressBarAsyncTask((Activity) mContext, subId);
        asyncTask.execute();
        proDialog.dismiss();
    }

    private void show() {
        proDialog =
                ProgressDialog.show(
                        mContext, "", mContext.getString(R.string.progress_dialog_waiting), true);
    }

    public class ProgressBarAsyncTask extends AsyncTask<Integer, Integer, String> {
        Activity mActivity;
        final int mSubId;
        String mMmsCapacity = null;
        List<Map<String, String>> storageList = new ArrayList<Map<String, String>>();

        public ProgressBarAsyncTask(Activity activity, final int subId) {
            super();
            mActivity = activity;
            mSubId = subId;
        }

        @Override
        protected String doInBackground(Integer... params) {
            if (PhoneUtils.getDefault().hasSim()) {
                String simSMSCapacity = "";
                simSMSCapacity = getSimCardSmsCapacityBySubId(mSubId);
                Map<String, String> SIMMap = new HashMap<String, String>();
                SIMMap.put(STORAGE_NAME, mActivity.getResources().getString(R.string.sim_storage));
                SIMMap.put(STORAGE_CAPACITY, simSMSCapacity);
                storageList.add(SIMMap);
            }

            ContentResolver cr = mActivity.getContentResolver();
            String[] projection = new String[] {BaseColumns._ID};
            Uri uri = Uri.parse(SMS_URL);
            Cursor cursor = cr.query(uri, projection, null, null, null);
            int phoneCapacity = cursor.getCount();
            Map<String, String> SmsMap = new HashMap<String, String>();
            SmsMap.put(STORAGE_NAME, mActivity.getResources().getString(R.string.sms_storage));
            SmsMap.put(
                    STORAGE_CAPACITY,
                    String.valueOf(phoneCapacity)
                            + mActivity.getResources().getString(R.string.capacity_counts));
            storageList.add(SmsMap);
            cursor.close();

            getMmsMemoryUsageInfo();
            Map<String, String> MMSMap = new HashMap<String, String>();
            MMSMap.put(STORAGE_NAME, mActivity.getResources().getString(R.string.mms_storage));
            MMSMap.put(STORAGE_CAPACITY, mMmsCapacity);
            storageList.add(MMSMap);
            return null;
        }

        @Override
        protected void onPreExecute() {
            proDialog =
                    ProgressDialog.show(
                            mActivity,
                            "",
                            mActivity.getString(R.string.progress_dialog_waiting),
                            true);
        }

        @Override
        protected void onPostExecute(String result) {
            proDialog.dismiss();
            LinearLayout linearLayoutMain = new LinearLayout(mActivity);
            linearLayoutMain.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT));
            ListView listView = new ListView(mActivity);

            SimpleAdapter adapter =
                    new SimpleAdapter(
                            mActivity,
                            storageList,
                            R.layout.storage_capacity_list_item_view,
                            new String[] {STORAGE_NAME, STORAGE_CAPACITY},
                            new int[] {R.id.storage_name, R.id.storage_capacity});

            listView.setAdapter(adapter);

            linearLayoutMain.addView(listView);
            String title = mActivity.getResources().getString(R.string.storage_capacity_pref_title);

            new AlertDialog.Builder(mActivity)
                    .setTitle(title)
                    .setView(linearLayoutMain)
                    .setNegativeButton(
                            android.R.string.ok,
                            new AlertDialog.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                    .show();
        }

        private String getSimCardSmsCapacityBySubId(int subId) {
            String ret = "";
            int totalCount = 0;
            int messageCount = 0;

            Uri simUri =
                    (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                            ? ICC_URI
                            : ContentUris.withAppendedId(ICC_SUB_URI, subId));
            ContentResolver cr = mActivity.getContentResolver();
            Cursor cursor = cr.query(simUri, null, null, null, null);
            messageCount = cursor.getCount();
            cursor.close();

            final SmsManager smsManager = PhoneUtils.get(subId).getSmsManager();
            totalCount = smsManager.getSmsCapacityInIcc();

            ret =
                    messageCount
                            + mActivity.getResources().getString(R.string.capacity_counts)
                            + " / "
                            + String.valueOf(totalCount)
                            + mActivity.getResources().getString(R.string.capacity_counts);
            return ret;
        }

        private void getMmsMemoryUsageInfo() {
            ContentResolver cr = mActivity.getContentResolver();
            long mmsSize = 0;
            Uri conversationUri = null;
            Uri mmsUri = null;
            int threadId = -1;
            String[] PROJECTION = new String[] {MmsSms.TYPE_DISCRIMINATOR_COLUMN, BaseColumns._ID};
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long blockCount = stat.getBlockCount();

            long totalSize = (long) stat.getBlockSize() * (long) stat.getBlockCount();
            if (totalSize < 0) {
                totalSize = 0;
            }

            // Get conversations
            Cursor conversationCursor =
                    cr.query(MmsSms.CONTENT_CONVERSATIONS_URI, null, null, null, null);

            if (conversationCursor == null) {
                return;
            }
            while (conversationCursor.moveToNext() && !(mActivity.isFinishing())) {
                threadId = conversationCursor.getColumnIndex("thread_id");
                if (threadId == -1 || conversationCursor.isNull(threadId)) {
                    LogUtil.e(TAG, "cursor skip, threadId: " + threadId);
                    continue;
                }
                conversationUri =
                        ContentUris.withAppendedId(
                                MmsSms.CONTENT_CONVERSATIONS_URI,
                                conversationCursor.getLong(threadId));

                // Get messages in conversation
                Cursor messageCursor = cr.query(conversationUri, PROJECTION, null, null, null);
                if (messageCursor != null) {
                    // Loop through each message
                    while (messageCursor.moveToNext()) {
                        mmsUri =
                                ContentUris.withAppendedId(
                                        Mms.CONTENT_URI,
                                        messageCursor.getLong(
                                                messageCursor.getColumnIndex(BaseColumns._ID)));
                        if ("mms"
                                .equals(
                                        messageCursor.getString(
                                                messageCursor.getColumnIndexOrThrow(
                                                        MmsSms.TYPE_DISCRIMINATOR_COLUMN)))) {
                            DatabaseMessages.MmsMessage mms = MmsUtils.loadMms(mmsUri);
                            LogUtil.i(TAG, "mms size : " + mms.getSize());
                            mmsSize += mms.getSize();
                        }
                    }
                    messageCursor.close();
                }
            }
            mMmsCapacity = formatSize(mmsSize) + " / " + formatSize(totalSize);

            conversationCursor.close();
        }

        private String formatSize(long size) {
            return Formatter.formatFileSize(mActivity, size);
        }
    }
}
