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

package com.android.messaging.ui.appsettings;

import android.content.Context;
import android.preference.EditTextPreference;
import android.telephony.SmsManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;

import com.android.messaging.R;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;

/**
 * Preference that displays and updates SMSC address in a SIM card.
 */
public class SmscAddressPreference extends EditTextPreference {
    /**
     * Maximum length for only number digits excluding the '+' sign in BCD format. The length of
     * SMSC address is 12 bytes(See 3GPP 31.102, 4.2.27 EF-SMSP). It consists of one byte SMSC
     * length, one byte TON(Type Of Number) and 10 bytes number digits in BCD format excluding the
     * '+' sign.
     */
    private static final int SMSC_MAX_LEN_EXCLUDING_PLUS_SIGN = 20; // 10 bytes * 2 in BCD format.

    /**
     * Default SMSC address. It is used before reading SMSC address from a SIM and for error case.
     */
    private static final String DEFAULT_SMSC_ADDRESS = "";

    /**
     * Cached SMSC address value. SMSC address in a SIM card is read and copied to this via calling
     * initSmsAddressPreference() from PerSubscriptionSettingsActivity once and whenever an user
     * updates it manually.
     */
    private static String sSmscAddress;

    private int mSubId;

    public SmscAddressPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        sSmscAddress = DEFAULT_SMSC_ADDRESS;
    }

    /**
     * Initializes an SmscAddressPreference. It reads SMSC address from a SIM associated with given
     * subId and refreshes the preference.
     *
     * @param subId subscription Id for this preference.
     */
    public void initSmsAddressPreference(final int subId) {
        mSubId = subId;
        refreshSmscAddress();
    }

    @Override
    protected void onBindView(final View view) {
        // Set the value as the summary and let the superclass populate the views.
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        setSummary(bidiFormatter.unicodeWrap(sSmscAddress, TextDirectionHeuristicsCompat.LTR));
        super.onBindView(view);
    }

    @Override
    protected void onDialogClosed(final boolean positiveResult) {
        if (positiveResult) {
            final String newSmscAddress = filter(getEditText().getText().toString());
            if (!TextUtils.isEmpty(newSmscAddress) && !sSmscAddress.equals(newSmscAddress)) {
                new AsyncSetSmscAddressTask(this).executeOnThreadPool(newSmscAddress, null, null);
            }
        }
    }

    @Override
    public void setText(final String smscAddress) {
        sSmscAddress = smscAddress;
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        super.setText(bidiFormatter.unicodeWrap(sSmscAddress, TextDirectionHeuristicsCompat.LTR));
    }

    private String filter(final String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }

        int count = 0;
        int len = address.length();
        StringBuilder ret = new StringBuilder(len);

        if (len > 0) {
            int index = 0;
            char c;
            // Filter out before first allowed char('0' ~ '9' or '+').
            while (index < len) {
                c = address.charAt(index++);
                if ((c >= '0' && c <= '9') || c == '+') {
                    ret.append(c);
                    if (c != '+') {
                        count++;
                    }
                    break;
                }
            }

            // Allow only digit.
            for (; index < len && count < SMSC_MAX_LEN_EXCLUDING_PLUS_SIGN; index++) {
                c = address.charAt(index);
                if (c >= '0' && c <= '9') {
                    ret.append(c);
                    count++;
                }
            }
        }
        return ret.toString();
    }

    public int getSubId() {
        return mSubId;
    }

    public void refreshSmscAddress() {
        new AsyncGetSmscAddressTask(this).executeOnThreadPool(null, null, null);
    }

    private static class AsyncGetSmscAddressTask extends SafeAsyncTask<Void, Void, String> {
        private final SmscAddressPreference mPref;
        private final SmsManager mSmsManager;
        private final int mSubId;

        public AsyncGetSmscAddressTask(final SmscAddressPreference pref) {
            mPref = pref;
            mSubId = pref.getSubId();
            mSmsManager = PhoneUtils.get(mSubId).getSmsManager();
        }

        @Override
        protected String doInBackgroundTimed(Void... params) {
            try {
                return mSmsManager.getSmscAddress();
            } catch (RuntimeException e) {
                LogUtil.e(LogUtil.BUGLE_TAG, "Failed to get SMSC for subId=" + mSubId, e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String smscAddress) {
            LogUtil.d(LogUtil.BUGLE_TAG, "Get SMSC address is "
                        + (smscAddress == null ? "failed" : "succeeded") + " for subId=" + mSubId);
            if (smscAddress == null) {
                UiUtils.showToastAtBottom(
                        mPref.getContext().getString(R.string.fail_get_smsc_address_message));
                mPref.setText(DEFAULT_SMSC_ADDRESS);
            } else {
                mPref.setText(smscAddress);
            }
        }
    }

    private static class AsyncSetSmscAddressTask extends SafeAsyncTask<String, Void, Boolean> {
        private final SmscAddressPreference mPref;
        private final SmsManager mSmsManager;
        private final int mSubId;

        public AsyncSetSmscAddressTask(final SmscAddressPreference pref) {
            mPref = pref;
            mSubId = pref.getSubId();
            mSmsManager = PhoneUtils.get(mSubId).getSmsManager();
        }

        @Override
        protected Boolean doInBackgroundTimed(String... params) {
            try {
                return mSmsManager.setSmscAddress(params[0]);
            } catch (RuntimeException e) {
                LogUtil.e(LogUtil.BUGLE_TAG, "Failed to set SMSC for subId=" + mSubId, e);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            LogUtil.d(LogUtil.BUGLE_TAG, "Set SMSC address is "
                        + (result ? "succeeded" : "failed") + " for subId=" + mSubId);
            if (!result) {
                UiUtils.showToastAtBottom(
                        mPref.getContext().getString(R.string.fail_set_smsc_address_message));
            }
            // Refresh always.
            mPref.refreshSmscAddress();
        }
    }
}
