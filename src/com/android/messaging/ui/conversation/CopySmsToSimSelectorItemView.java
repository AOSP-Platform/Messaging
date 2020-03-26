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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.datamodel.media.AvatarRequestDescriptor;
import com.android.messaging.ui.AsyncImageView;;
import com.android.messaging.util.Assert;

/**
 * Shows a view for a SIM in the Copy SMS to SIM selector.
 */
public class CopySmsToSimSelectorItemView extends LinearLayout {
    private SubscriptionListEntry mData;
    private AsyncImageView mSimIconView;
    private TextView mNameTextView;
    private TextView mDetailsTextView;

    public CopySmsToSimSelectorItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mSimIconView = (AsyncImageView) findViewById(R.id.sim_icon);
        mNameTextView = (TextView) findViewById(R.id.name);
        mDetailsTextView = (TextView) findViewById(R.id.details);
    }

    public void bind(final SubscriptionListEntry simEntry) {
        Assert.notNull(simEntry);
        mData = simEntry;
        updateViewAppearance();
    }

    private void updateViewAppearance() {
        Assert.notNull(mData);
        final String displayName = mData.displayName;
        if (TextUtils.isEmpty(displayName)) {
            mNameTextView.setVisibility(GONE);
        } else {
            mNameTextView.setVisibility(VISIBLE);
            mNameTextView.setText(displayName);
        }

        final String details = mData.displayDestination;
        if (TextUtils.isEmpty(details)) {
            mDetailsTextView.setVisibility(GONE);
        } else {
            mDetailsTextView.setVisibility(VISIBLE);
            mDetailsTextView.setText(details);
        }

        int iconSize =
                (int) getResources().getDimension(R.dimen.copy_sms_to_sim_selector_icon_size);
        mSimIconView.setImageResourceId(
                new AvatarRequestDescriptor(mData.iconUri, iconSize, iconSize));
    }
}
