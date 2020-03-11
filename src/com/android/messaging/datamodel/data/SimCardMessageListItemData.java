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

package com.android.messaging.datamodel.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.text.TextUtils;

import com.android.messaging.datamodel.data.ParticipantData;

/**
 * Data object used to power SimCardMessageListItemView.
 */
public class SimCardMessageListItemData {
    private String mAddress;
    private String mBody;
    private long mDate;
    private int mIndexOnIcc;
    private int mType;

    // Please refer to com.android.providers.telephony.SmsProvider.java
    private static final int INDEX_SERVICE_CENTER_ADDRESS = 0;
    private static final int INDEX_ADDRESS = 1;
    private static final int INDEX_MESSAGE_CLASS = 2;
    private static final int INDEX_BODY = 3;
    private static final int INDEX_DATE = 4;
    private static final int INDEX_STATUS = 5;
    private static final int INDEX_INDEX_ON_ICC = 6;
    private static final int INDEX_IS_STATUS_REPORT = 7;
    private static final int INDEX_TRANSPORT_TYPE = 8;
    private static final int INDEX_TYPE = 9;
    private static final int INDEX_LOCKED = 10;
    private static final int INDEX_ERROR_CODE = 11;
    private static final int INDEX_ID = 12;

    public static final Uri ICC_URI = Uri.parse("content://sms/icc");
    public static final Uri ICC_SUBID_URI = Uri.parse("content://sms/icc_subId");

    public static final String SORT_ORDER_BY_INDEX_ON_ICC = INDEX_INDEX_ON_ICC + " ASC";

    public SimCardMessageListItemData() {
    }

    public SimCardMessageListItemData(SimCardMessageListItemData data) {
        mAddress = data.mAddress;
        mBody = data.mBody;
        mDate = data.mDate;
        mIndexOnIcc = data.mIndexOnIcc;
        mType = data.mType;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SimCardMessageListItemData
                && mIndexOnIcc == ((SimCardMessageListItemData) obj).mIndexOnIcc;
    }

    @Override
    public int hashCode() {
        return mIndexOnIcc;
    }

    /** Bind to a sim card message cursor in the sim card message list. */
    public void bind(final Cursor cursor) {
        mAddress = cursor.getString(INDEX_ADDRESS);
        if (TextUtils.isEmpty(mAddress)) {
            mAddress = ParticipantData.getUnknownSenderDestination();
        }
        mBody = cursor.getString(INDEX_BODY);
        mDate = cursor.getLong(INDEX_DATE);
        mIndexOnIcc = cursor.getInt(INDEX_INDEX_ON_ICC);
        mType = cursor.getInt(INDEX_TYPE);
    }

    public String getMessageBody() {
        return mBody;
    }

    public String getAddress() {
        return mAddress;
    }

    public int getIndexOnIcc() {
        return mIndexOnIcc;
    }

    public boolean isOutgoing() {
        return mType != Sms.MESSAGE_TYPE_INBOX;
    }

    public boolean isUnsent() {
        return mType == Sms.MESSAGE_TYPE_OUTBOX;
    }

    public ContentValues toContentValues() {
        final ContentValues values = new ContentValues();
        values.put(Sms.BODY, mBody);
        values.put(Sms.ADDRESS, mAddress);
        if (mType == Sms.MESSAGE_TYPE_INBOX) {
            values.put(Sms.Inbox.DATE, mDate);
        } else {
            values.put(Sms.DATE, System.currentTimeMillis());
        }
        return values;
    }
}
