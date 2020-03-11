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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.SimCardMessageListItemData;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Bridges between the sim card message cursor loaded by the inner class CursorLoaderListFragment in
 * SimCardMessageListActivity and the SimCardMessageListItemView.
 */
public class SimCardMessageListAdapter extends CursorAdapter
        implements SimCardMessageListItemView.HostInterface {
    private final Set<SimCardMessageListItemData> mSelectedSimCardMessageItemDataSet;

    public SimCardMessageListAdapter(final Context context, final Cursor cursor) {
        super(context, cursor, 0 /* flags */);
        mSelectedSimCardMessageItemDataSet =
                Collections.synchronizedSet(new HashSet<SimCardMessageListItemData>());
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        Assert.isTrue(view instanceof SimCardMessageListItemView);
        ((SimCardMessageListItemView) view).bind(cursor, this);
    }

    @Override
    public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        return layoutInflater.inflate(R.layout.sim_card_message_list_item_view, parent, false);
    }

    @Override
    public boolean isItemSelected(final SimCardMessageListItemData data) {
        return mSelectedSimCardMessageItemDataSet.contains(data);
    }

    public void toggleSelection(final SimCardMessageListItemView view) {
        final SimCardMessageListItemData data = view.getData();
        if (mSelectedSimCardMessageItemDataSet.contains(data)) {
            mSelectedSimCardMessageItemDataSet.remove(data);
        } else {
            mSelectedSimCardMessageItemDataSet.add(new SimCardMessageListItemData(data));
        }
        view.updateBackground();
    }

    public boolean hasSelectedItems() {
        return !mSelectedSimCardMessageItemDataSet.isEmpty();
    }

    public void clearSelectedItems() {
        mSelectedSimCardMessageItemDataSet.clear();
    }

    public final Set<SimCardMessageListItemData> getSelectedSimCardMessageListItemDataSet() {
        return new HashSet<SimCardMessageListItemData>(mSelectedSimCardMessageItemDataSet);
    }

    @DoesNotRunOnMainThread
    public final Set<SimCardMessageListItemData> getAllSimCardMessageListItemDataSet() {
        HashSet<SimCardMessageListItemData> dataSet = new HashSet<SimCardMessageListItemData>();
        Cursor cursor = getCursor();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    SimCardMessageListItemData data = new SimCardMessageListItemData();
                    data.bind(cursor);
                    dataSet.add(data);
                } while (cursor.moveToNext());
            }
        }
        return dataSet;
    }
}
