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

import android.content.Context;
import android.content.res.Resources;
import android.preference.EditTextPreference;
import android.text.InputType;
import android.text.TextUtils;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Toast;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.PhoneUtils;

/**
 * Preference that displays call back phone number and allows editing via a dialog.
 *
 * <p>A default call back number can be assigned, which is shown in the preference view and used to
 * populate the dialog editor when the preference value is not set.
 */
public class CallBackPreference extends EditTextPreference {

    /* Minimum 7 digits should be entered for callback number exluding special
     * characters.
     */
    public static final int MIN_CALLBACK_INPUT_DIGITS = 7;
    /* Maximum number of characters user can enter for callback number are 20.
     * It includes digits and symbols both. TODO: check should these be digits only?
     */
    public static final int MAX_CALLBACK_INPUT_CHAR = 20;

    private int mSubId;

    public CallBackPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDefaultCallBackNumber(final int subId) {
        mSubId = subId;
    }

    @Override
    protected void onBindView(final View view) {
        // Show the preference value if it's set, or the default number if not.
        String value = getText();
        if (TextUtils.isEmpty(value)) {
            value = PhoneUtils.get(mSubId).getCanonicalForSelf(false /*allowOverride*/);
            // save the value to shared preference
            final BuglePrefs subPrefs = BuglePrefs.getSubscriptionPrefs(mSubId);
            final Context context = Factory.get().getApplicationContext();
            final Resources res = context.getResources();
            subPrefs.putString(res.getString(R.string.call_back_num_key), value);
        }
        final String displayValue = PhoneUtils.get(mSubId).formatForDisplay(value);
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        final String phoneNumber =
                bidiFormatter.unicodeWrap(displayValue, TextDirectionHeuristicsCompat.LTR);
        // Set the value as the summary and let the superclass populate the views
        setSummary(phoneNumber);
        super.onBindView(view);
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);

        final String value = getText();
        if (TextUtils.isEmpty(value)) {
            final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
            final String phoneNumber =
                    bidiFormatter.unicodeWrap(
                            PhoneUtils.get(mSubId).getCanonicalForSelf(false /*allowOverride*/),
                            TextDirectionHeuristicsCompat.LTR);
            getEditText().setText(phoneNumber);
        }
        getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        getEditText()
                .setFilters(
                        new InputFilter[] {new InputFilter.LengthFilter(MAX_CALLBACK_INPUT_CHAR)});
    }

    @Override
    protected void onDialogClosed(final boolean positiveResult) {
        final String value = getEditText().getText().toString();
        if (TextUtils.isEmpty(value)) {
            // Donot update
            return;
        }
        // Check minimum number of digits present
        boolean minLenNotSet = !isMinDigitLength(value);
        // if special character present or length is less than minimum, notify user
        if (isSpecialCharPresent(value) || minLenNotSet) {
            final Context context = Factory.get().getApplicationContext();
            Toast.makeText(
                            context,
                            minLenNotSet
                                    ? R.string.min_length_call_back
                                    : R.string.special_char_call_back,
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        super.onDialogClosed(positiveResult);
    }

    /** Check for special characters '/' ',' '$' '_' '\' in number string */
    private boolean isSpecialCharPresent(String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == '/' || c == ',' || c == '$' || c == '_' || c == '\\') {
                return true;
            }
        }
        return false;
    }

    /**
     * Check minimum number of digits entered by user. Minimum number should be greater than or
     * equal to MIN_CALLBACK_INPUT_DIGITS. We will remove special characters and will count only
     * digits between 0-9.
     */
    private boolean isMinDigitLength(String value) {
        int len = value.length();
        int minLen = 0;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                minLen++;
            }
        }
        return minLen >= MIN_CALLBACK_INPUT_DIGITS ? true : false;
    }
}
