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
package com.android.messaging.ui.simcard;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModelImpl;
import com.android.messaging.datamodel.data.SimCardMessageListItemData;

/**
 * The view for a single entry in a sim card message list.
 */
public class SimCardMessageListItemView extends LinearLayout {
    /** Implemented by the owner of this SimCardMessageListItemView instance. */
    public interface HostInterface {
        boolean isItemSelected(final SimCardMessageListItemData data);
    }

    private final SimCardMessageListItemData mData;

    private HostInterface mHostInterface;
    private TextView mAddressTextView;
    private ImageView mTypeIconImageView;
    private TextView mBodyTextView;

    public SimCardMessageListItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mData = new SimCardMessageListItemData();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAddressTextView = (TextView) findViewById(R.id.address);
        mTypeIconImageView = (ImageView) findViewById(R.id.type_icon);
        mBodyTextView = (TextView) findViewById(R.id.body);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        updateViewAppearance();
    }

    /**
     * Fills in the data associated with this view by binding to a sim card message cursor.
     *
     * @param cursor the sim card message cursor.
     * @param hostInterface the host interface to this view.
     */
    public void bind(final Cursor cursor, final HostInterface hostInterface) {
        mData.bind(cursor);
        mHostInterface = hostInterface;
        updateViewAppearance();
    }

    private void updateViewAppearance() {
        if (mData.isOutgoing()) {
            mTypeIconImageView.setImageResource(R.drawable.ic_outgoing_light);
        } else {
            mTypeIconImageView.setImageResource(R.drawable.ic_incoming_light);
        }

        // Show raw address without any formatting.
        mAddressTextView.setText(mData.getAddress());
        mBodyTextView.setText(mData.getMessageBody());

        updateBackground();
    }

    public void updateBackground() {
        if (mHostInterface.isItemSelected(mData)) {
            setBackgroundColor(
                    getResources().getColor(R.color.sim_card_message_selected_background));
        } else {
            setBackgroundColor(Color.TRANSPARENT);
        }
    }

    public SimCardMessageListItemData getData() {
        return mData;
    }
}
