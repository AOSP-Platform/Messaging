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
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.CursorAdapter;

import com.android.messaging.R;

/**
 * The back-end data adapter for {@link SimMessageListActivity}.
 */
public class SimMessageAdapter extends CursorAdapter {

    public SimMessageAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        SimMessage message = SimMessage.createFromCursor(cursor);
        SimMessageListItem listItem = (SimMessageListItem) LayoutInflater.from(context).inflate(R.layout.sim_message_list_item, parent, false);
        listItem.bind(message);
        return listItem;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        SimMessage message = SimMessage.createFromCursor(cursor);
        SimMessageListItem listItem = (SimMessageListItem) view;
        listItem.bind(message);
    }

}

