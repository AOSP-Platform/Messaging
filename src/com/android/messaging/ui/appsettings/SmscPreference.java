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

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;

import com.android.messaging.R;

/**
 * Preference that displays a smsc and allows editing via a dialog.
 */
public class SmscPreference extends EditTextPreference {

    private String mDefaultSmsc;
    private int mSubId;

    public SmscPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mDefaultSmsc = "";
    }

    public void setDefaultSmsc(final String smsc, final int subscriptionId) {
        mDefaultSmsc = smsc;
        mSubId = subscriptionId;
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        if (!TextUtils.isEmpty(mDefaultSmsc)) {
            final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
            final String smsc = bidiFormatter.unicodeWrap(mDefaultSmsc, TextDirectionHeuristicsCompat.LTR);
            getEditText().setText(smsc);
        }
        getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
    }

    @Override
    protected void onDialogClosed(final boolean positiveResult) {
        final String value = getEditText().getText().toString();
        if (positiveResult) {
            setText(value);
        }
        super.onDialogClosed(positiveResult);
    }
}
