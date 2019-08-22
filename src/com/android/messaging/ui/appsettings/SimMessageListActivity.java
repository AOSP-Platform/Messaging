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

import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony.Sms;
import android.telephony.SubscriptionManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.CursorAdapter;
import androidx.core.app.NavUtils;

import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.mmslib.SqliteWrapper;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.util.LogUtil;
import com.android.messaging.ui.UIIntents;

import com.android.messaging.R;

/**
 * Displays a list of the SMS messages stored on the ICC.
 */
public class SimMessageListActivity extends BugleActionBarActivity {

    public static final Uri ICC_URI = Uri.parse("content://sms/icc");
    public static final Uri ICC_SUB_URI = Uri.parse("content://sms/icc_sub");
    public static final Uri ICC_SUCCESS_URI = Uri.parse("content://sms/icc/success");

    public static final String SIM_MSG_TAG = "SmsOnSim";
    private static ContentResolver mContentResolver;


    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContentResolver();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.sim_messages_activity_title));
        
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final CursorLoaderListFragment fragment = new CursorLoaderListFragment();
        ft.replace(android.R.id.content, fragment);
        ft.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
    * List fragment queries SQLite database on worker thread.
    */
    public static class CursorLoaderListFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

        private static final int MENU_DELETE_FROM_SIM = 0;

        private int mSubId;
        private TextView mEmptyView;
        private ProgressDialog mProDialog;

        // Adapter to display the list's data.
        CursorAdapter mAdapter;


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Get sub id from launch intent
            final Intent intent = getActivity().getIntent();
            mSubId = (intent != null) ? intent.getIntExtra(UIIntents.UI_INTENT_EXTRA_SUB_ID,
                    ParticipantData.DEFAULT_SELF_SUB_ID) : ParticipantData.DEFAULT_SELF_SUB_ID;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
                View view = inflater.inflate(R.layout.sim_list, container, false);
                mEmptyView = (TextView) view.findViewById(R.id.empty_message);    
            return view;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // Set context menu for long-press.
            ListView listView = getListView();
            listView.setOnCreateContextMenuListener(mOnCreateContextMenuListener);

            // Create a cursor adapter to display the loaded data.
            mAdapter = new SimMessageAdapter(getActivity(), null);
            setListAdapter(mAdapter);

            // Prepare the loader.  Either re-connect with an existing one,
            // or start a new one.
            getLoaderManager().initLoader(0, null, this);
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            showProgressDialog();
            Uri subUri = ContentUris.withAppendedId(ICC_SUB_URI, mSubId);
            return new CursorLoader(getActivity(), (mSubId ==
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID ? ICC_URI : subUri),
                            null, null, null, Sms.DEFAULT_SORT_ORDER);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            // Swap the new cursor in. Framework will take care of closing the
            // old cursor once we return.
            mAdapter.swapCursor(data);
            getActivity().invalidateOptionsMenu();
            hideProgressDialog();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // This is called when the last Cursor provided to onLoadFinished()
            // above is about to be closed.  We need to make sure we are no
            // longer using it.
            mAdapter.swapCursor(null);
        }

        private final OnCreateContextMenuListener mOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v,
                    ContextMenuInfo menuInfo) {
                menu.add(0, MENU_DELETE_FROM_SIM, 0, R.string.sim_delete);
            }
        };
                
        @Override
        public boolean onContextItemSelected(MenuItem item) {
            Cursor cursor = mAdapter.getCursor();

            confirmDeleteDialog(
                    new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteMessageFromSim(cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc")));
                            dialog.dismiss();
                        }
                    },
                    R.string.confirm_delete_sim_message);
            return super.onContextItemSelected(item);
        }

        private void showProgressDialog() {
            //Show refreshing status
            mProDialog = ProgressDialog.show(getContext(), getString(R.string.sim_progress_dialog_title),
                    getString(R.string.sim_progress_dialog_message), true);
        }

        private void hideProgressDialog() {
            // Hide refreshing status
            mProDialog.dismiss();
            
            // Check if we need to show empty list
            if (mAdapter != null) {
                Cursor cursor = mAdapter.getCursor();
                if (cursor == null ||(cursor.getCount() < 1)) {
                    mEmptyView.setVisibility(View.VISIBLE);
                } else {
                    mEmptyView.setVisibility(View.GONE);
                }
            }
        }

        private void deleteMessageFromSim(String indexString) {
            Uri simUri = (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? ICC_URI :
                    ContentUris.withAppendedId(ICC_SUB_URI, mSubId));
            int result = 0;     //failed
            LogUtil.v(SIM_MSG_TAG, "delete uri:" + simUri + " messageIndex:" + indexString);
            result = SqliteWrapper.delete(getActivity(), mContentResolver,
                    simUri.buildUpon().appendPath(indexString).build(), null, null);
            LogUtil.v(SIM_MSG_TAG, "deleteMessageFromSim " + (result == 1 ? "success" : "failed"));
        }

        private void confirmDeleteDialog(OnClickListener listener, int messageId) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.confirm_dialog_title);
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setCancelable(true);
            builder.setPositiveButton(R.string.yes, listener);
            builder.setNegativeButton(R.string.no, null);
            builder.setMessage(messageId);
            builder.show();
        }

    }
}
