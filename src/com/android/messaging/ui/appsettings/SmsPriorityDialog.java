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

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;

import com.android.messaging.R;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BuglePrefs;

/** Displays Sms Priority Options dailog. */
public class SmsPriorityDialog {
    private final Context mContext;
    private final int mSubId;
    private AlertDialog mDialog;

    public static void showDialog(final Context context, final int subId) {
        new SmsPriorityDialog(context, subId).show();
    }

    private SmsPriorityDialog(final Context context, final int subId) {
        mContext = context;
        mSubId = subId;
    }

    private void show() {
        Assert.isNull(mDialog);
        mDialog =
                new AlertDialog.Builder(mContext)
                        .setView(createView())
                        .setTitle(R.string.sms_priority_pref_title)
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
    }

    private void SmsPriorityValue(final String priority) {
        Assert.notNull(mDialog);
        BuglePrefs.getSubscriptionPrefs(mSubId)
                .putString(mContext.getString(R.string.sms_priority_pref_key), priority);
        mDialog.dismiss();
    }

    private View createView() {
        final LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View rootView = inflater.inflate(R.layout.sms_priority_dialog, null, false);
        final RadioButton priorityEmergencyButton =
                (RadioButton) rootView.findViewById(R.id.sms_priority_emergency_button);
        final RadioButton priorityUrgentButton =
                (RadioButton) rootView.findViewById(R.id.sms_priority_urgent_button);
        final RadioButton priorityInteractiveButton =
                (RadioButton) rootView.findViewById(R.id.sms_priority_interactive_button);
        final RadioButton priorityNormalButton =
                (RadioButton) rootView.findViewById(R.id.sms_priority_normal_button);
        priorityEmergencyButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SmsPriorityValue(mContext.getString(R.string.priority_emergency));
                    }
                });
        priorityUrgentButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SmsPriorityValue(mContext.getString(R.string.priority_urgent));
                    }
                });
        priorityInteractiveButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SmsPriorityValue(mContext.getString(R.string.priority_interactive));
                    }
                });
        priorityNormalButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SmsPriorityValue(mContext.getString(R.string.priority_normal));
                    }
                });
        final String priorityEnabled =
                BuglePrefs.getSubscriptionPrefs(mSubId)
                        .getString(
                                mContext.getString(R.string.sms_priority_pref_key),
                                mContext.getString(R.string.priority_normal));
        switch (priorityEnabled) {
            case MmsUtils.STRING_PRIORITY_EMERGENCY:
                priorityEmergencyButton.setChecked(true);
                break;
            case MmsUtils.STRING_PRIORITY_URGENT:
                priorityUrgentButton.setChecked(true);
                break;
            case MmsUtils.STRING_PRIORITY_INTERACTIVE:
                priorityInteractiveButton.setChecked(true);
                break;
            case MmsUtils.STRING_PRIORITY_NORMAL:
                priorityNormalButton.setChecked(true);
        }
        return rootView;
    }
}
