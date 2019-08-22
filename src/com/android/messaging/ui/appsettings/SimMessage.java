/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.database.Cursor;
import android.provider.Telephony.Sms;
import android.provider.Telephony.TextBasedSmsColumns;

import com.android.messaging.util.Dates;
import com.android.messaging.util.LogUtil;

/**
 * Sim message object used to power SimMessageListItem view, which may be displayed in
 * sim list.
 */
public class SimMessage {

    private String mBody;
    private String mFrom;
    private String mDate;
    private int mType;
  

    public SimMessage (String body, String from, String date, int type) {
        mBody = body;
        mFrom = from;
        mDate = date;
        mType = type;
    }

    public static SimMessage createFromCursor(Cursor cursor) {
        String body = "";
        String from = "";
        String date = "";
        int type = TextBasedSmsColumns.MESSAGE_TYPE_ALL;

        try {
            int bodyColIndex = cursor.getColumnIndexOrThrow(Sms.BODY);
            body = cursor.getString(bodyColIndex);
        } catch (IllegalArgumentException e) {
            LogUtil.w(SimMessageListActivity.SIM_MSG_TAG, e.getMessage());
        }

        try {
            int addressColIndex =  cursor.getColumnIndexOrThrow(Sms.ADDRESS);
            from = cursor.getString(addressColIndex);
        } catch (IllegalArgumentException e) {
             LogUtil.w(SimMessageListActivity.SIM_MSG_TAG, e.getMessage());
        }

        try {
            int dateColIndex = cursor.getColumnIndexOrThrow(Sms.DATE);
            date = Dates.getMessageTimeString(cursor.getLong(dateColIndex)).toString();
        } catch (IllegalArgumentException e) {
             LogUtil.w(SimMessageListActivity.SIM_MSG_TAG, e.getMessage());
        }

        try {
            int typeColIndex = cursor.getColumnIndexOrThrow(Sms.TYPE);
            type = cursor.getInt(typeColIndex);
        } catch (IllegalArgumentException e) {
             LogUtil.w(SimMessageListActivity.SIM_MSG_TAG, e.getMessage());
        }

        return new SimMessage(body, from, date, type);
    }

    public String getBodyString() {
        return mBody;
    }

    public String getFromString() {
        return mFrom;
    }

    public String getDateString() {
        // Date string only present for delivered message
        return isOutgoingMessage() == true ? "" : mDate;
    }

    public int getTypeString() {
        return mType;
    }

    public boolean isOutgoingMessage() {
        return  (mType == TextBasedSmsColumns.MESSAGE_TYPE_FAILED)
                    || (mType == TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
                    || (mType == TextBasedSmsColumns.MESSAGE_TYPE_SENT)
                    || (mType == TextBasedSmsColumns.MESSAGE_TYPE_QUEUED);
    }
   
}

