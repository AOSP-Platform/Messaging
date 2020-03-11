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

import android.app.AlertDialog;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony.Sms;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CursorAdapter;
import android.widget.ListView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.SimCardMessageListItemData;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.ListEmptyView;
import com.android.messaging.ui.SnackBar;
import com.android.messaging.ui.UIIntents;

import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;

import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;

/**
 * This activity provides a list view of message in a sim card. Most of the work is handled in the
 * inner CursorLoaderListFragment class.
 */
public class SimCardMessageListActivity extends BugleActionBarActivity {
    private static final int REQUEST_SET_DEFAULT_SMS_APP = 1;

    private CursorLoaderListFragment mListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get subId from launch intent
        final Intent intent = getIntent();
        int subId = ParticipantData.DEFAULT_SELF_SUB_ID;
        if (intent != null) {
            subId = intent.getIntExtra(
                            UIIntents.UI_INTENT_EXTRA_SUB_ID, ParticipantData.DEFAULT_SELF_SUB_ID);
        }
        mListFragment = new CursorLoaderListFragment(subId);
        getFragmentManager().beginTransaction().add(android.R.id.content, mListFragment).commit();
        invalidateActionBar();
    }

    @Override
    protected void updateActionBar(final ActionBar actionBar) {
        actionBar.setTitle(getString(R.string.sim_card_message_list_activity_title));
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setBackgroundDrawable(
                new ColorDrawable(getResources().getColor(R.color.action_bar_background_color)));
        actionBar.show();
        super.updateActionBar(actionBar);
    }

    @Override
    public void onBackPressed() {
        if (isInMultiSelectionMode()) {
            exitMultiSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        // No-op
    }

    private void enterMultiSelectionMode() {
        super.startActionMode(mListFragment);
    }

    private void exitMultiSelectionMode() {
        dismissActionMode();
        final ListView listView = mListFragment.getListView();
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    private boolean isInMultiSelectionMode() {
        return getActionMode() != null;
    }

    /** List fragment queries a sim card messages list on worker thread. */
    public static class CursorLoaderListFragment extends ListFragment
            implements Callback, LoaderManager.LoaderCallbacks<Cursor> {
        private final int mSubId;

        // The parent activity
        private SimCardMessageListActivity mActivity;

        // This is the Adapter being used to display the list's data.
        private SimCardMessageListAdapter mAdapter;

        public CursorLoaderListFragment(int subId) {
            mSubId = subId;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mActivity = (SimCardMessageListActivity) getActivity();
            // Create a cursor adapter to display the loaded data.
            mAdapter = new SimCardMessageListAdapter(mActivity, null);
        }

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // We have a menu item to show in action bar.
            setHasOptionsMenu(true);
            return inflater.inflate(R.layout.sim_card_message_list_view, container, false);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            getListView()
                    .setOnItemLongClickListener(
                            new OnItemLongClickListener() {
                                @Override
                                public boolean onItemLongClick(
                                        AdapterView<?> parent, View view, int position, long id) {
                                    toggleSelection((SimCardMessageListItemView) view);
                                    return true;
                                }
                            });

            ListEmptyView emptyView = (ListEmptyView) mActivity.findViewById(R.id.empty_view);
            emptyView.setImageHint(R.drawable.ic_oobe_conv_list);
            emptyView.setTextHint(R.string.sim_card_message_list_empty_text);
            emptyView.setVisibility(View.INVISIBLE);

            setListAdapter(mAdapter);

            // Start out with a progress indicator.
            setListShown(false);

            // Prepare the loader.
            getLoaderManager().initLoader(0, null, this);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (!mActivity.isInMultiSelectionMode()) {
                return;
            }
            toggleSelection((SimCardMessageListItemView) v);
        }

        @Override
        public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
            if (mActivity.isInMultiSelectionMode()) {
                return;
            }

            inflater.inflate(R.menu.sim_card_message_list_menu, menu);
            menu.findItem(R.id.action_delete_all).setVisible(!mAdapter.isEmpty());
        }

        @Override
        public boolean onOptionsItemSelected(final MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_delete_all:
                    deleteSelectedSimCardMessagesFromSim(true /* deleteAll */);
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate the menu for the CAB
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.sim_card_message_list_select_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // Respond to clicks on the actions in the CAB
            switch (item.getItemId()) {
                case R.id.action_copy:
                    copySelectedSimCardMessagesToDevice();
                    return true;
                case R.id.action_delete:
                    deleteSelectedSimCardMessagesFromSim(false /* deleteAll */);
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mAdapter.clearSelectedItems();
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            final Uri uri =
                    mSubId == ParticipantData.DEFAULT_SELF_SUB_ID
                            ? SimCardMessageListItemData.ICC_URI
                            : ContentUris.withAppendedId(
                                    SimCardMessageListItemData.ICC_SUBID_URI, mSubId);
            return new CursorLoader(
                    mActivity,
                    uri,
                    null,
                    null,
                    null,
                    SimCardMessageListItemData.SORT_ORDER_BY_INDEX_ON_ICC) {

                @Override
                public Cursor loadInBackground() {
                    try {
                        return super.loadInBackground();
                    } catch (IllegalArgumentException e) {
                        // Nothing to do
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            mAdapter.swapCursor(cursor);
            mActivity.invalidateOptionsMenu();

            // The list should now be shown.
            setListShown(true);

            ListEmptyView emptyView = (ListEmptyView) mActivity.findViewById(R.id.empty_view);
            emptyView.setVisibility(mAdapter.isEmpty() ? View.VISIBLE : View.INVISIBLE);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mAdapter.swapCursor(null);
        }

        private void toggleSelection(final SimCardMessageListItemView view) {
            if (!mActivity.isInMultiSelectionMode()) {
                mActivity.enterMultiSelectionMode();
            }

            mAdapter.toggleSelection(view);

            if (!mAdapter.hasSelectedItems()) {
                mActivity.exitMultiSelectionMode();
            }
        }

        private void copySelectedSimCardMessagesToDevice() {
            // Allow it for the default sms app only.
            if (!PhoneUtils.getDefault().isDefaultSmsApp()) {
                showChangeDefaultSmsAppSnackBar();
                return;
            }

            final Set<SimCardMessageListItemData> selectedSimCardMessageListItemDataSet =
                    mAdapter.getSelectedSimCardMessageListItemDataSet();
            new AlertDialog.Builder(mActivity)
                    .setTitle(getResources().getQuantityString(
                                    R.plurals.copy_sim_card_messages_confirmation_dialog_title,
                                    selectedSimCardMessageListItemDataSet.size()))
                    .setPositiveButton(
                            R.string.copy_sim_card_messages_confirmation_button,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        final DialogInterface dialog, final int button) {
                                    new AsyncSimCardMessageTask(
                                            mActivity,
                                            mSubId,
                                            AsyncSimCardMessageTask.MODE_COPY)
                                            .executeOnThreadPool(
                                                    selectedSimCardMessageListItemDataSet,
                                                    null,
                                                    null);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void deleteSelectedSimCardMessagesFromSim(final boolean deleteAll) {
            // Allow it for the default sms app only.
            if (!PhoneUtils.getDefault().isDefaultSmsApp()) {
                showChangeDefaultSmsAppSnackBar();
                return;
            }

            final Set<SimCardMessageListItemData> selectedSimCardMessageListItemDataSet =
                    mAdapter.getSelectedSimCardMessageListItemDataSet();

            new AlertDialog.Builder(mActivity)
                    .setTitle(deleteAll
                            ? getString(
                                    R.string.delete_all_sim_card_messages_confirmation_dialog_title)
                            : getResources().getQuantityString(
                                    R.plurals.delete_sim_card_messages_confirmation_dialog_title,
                                    selectedSimCardMessageListItemDataSet.size()))
                    .setMessage(R.string.delete_sim_card_messages_confirmation_dialog_text)
                    .setPositiveButton(
                            R.string.delete_sim_card_messages_confirmation_button,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        final DialogInterface dialog, final int button) {
                                    // Start out with a progress indicator.
                                    setListShown(false);
                                    new AsyncSimCardMessageTask(
                                            mActivity,
                                            mSubId,
                                            deleteAll
                                                    ? AsyncSimCardMessageTask.MODE_DELETE_ALL
                                                    : AsyncSimCardMessageTask.MODE_DELETE)
                                            .executeOnThreadPool(
                                                    selectedSimCardMessageListItemDataSet,
                                                    null,
                                                    null);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showChangeDefaultSmsAppSnackBar() {
            UiUtils.showSnackBarWithCustomAction(
                    mActivity,
                    mActivity.getWindow().getDecorView().getRootView(),
                    getString(R.string.requires_default_sms_app),
                    SnackBar.Action.createCustomAction(
                            new Runnable() {
                                @Override
                                public void run() {
                                    final Intent intent =
                                            UIIntents.get().getChangeDefaultSmsAppIntent(mActivity);
                                    startActivityForResult(intent, REQUEST_SET_DEFAULT_SMS_APP);
                                }
                            },
                            getString(R.string.requires_default_sms_change_button)),
                    null /* interactions */,
                    null /* placement */);
        }
    }

    private static class AsyncSimCardMessageTask
            extends SafeAsyncTask<Set<SimCardMessageListItemData>, Void, Integer> {

        public static final int MODE_DELETE_ALL = 0;
        public static final int MODE_DELETE = 1;
        public static final int MODE_COPY = 2;

        private final Context mContext;
        private final int mSubId;
        private int mMode;

        public AsyncSimCardMessageTask(final Context context, final int subId, final int mode) {
            mContext = context;
            mSubId = subId;
            mMode = mode;
        }

        @Override
        protected Integer doInBackgroundTimed(final Set<SimCardMessageListItemData>... params) {
            final Iterator<SimCardMessageListItemData> iterator;
            final ContentResolver cr = mContext.getContentResolver();
            Uri uri;
            int failCount = 0;
            if (mMode == MODE_DELETE_ALL) {
                SimCardMessageListAdapter mAdapter =
                        (SimCardMessageListAdapter)
                                (((SimCardMessageListActivity) mContext).mListFragment)
                                        .getListAdapter();
                iterator = mAdapter.getAllSimCardMessageListItemDataSet().iterator();
                mMode = MODE_DELETE;
            } else {
                iterator = params[0].iterator();
            }

            if (mMode == MODE_DELETE) {
                uri = mSubId == ParticipantData.DEFAULT_SELF_SUB_ID
                            ? SimCardMessageListItemData.ICC_URI
                            : ContentUris.withAppendedId(
                                    SimCardMessageListItemData.ICC_SUBID_URI, mSubId);
                while (iterator.hasNext()) {
                    int index = iterator.next().getIndexOnIcc();
                    if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                        LogUtil.d(LogUtil.BUGLE_TAG, "Delete SMS in ICC at index " + index
                                + ", subId=" + mSubId);
                    }

                    int ret = 0;
                    try {
                        ret = cr.delete(ContentUris.withAppendedId(uri, index), null, null);
                    } catch (IllegalArgumentException e) {
                        // Nothing to do
                    }
                    if (ret < 1) {
                        failCount++;
                        LogUtil.e(LogUtil.BUGLE_TAG, "Failed to delete SMS in ICC"
                                + ", uri=" + ContentUris.withAppendedId(uri, index));
                    }
                }
            } else { // MODE_COPY
                while (iterator.hasNext()) {
                    SimCardMessageListItemData data = iterator.next();
                    if (data != null) {
                        final ContentValues messageValues = data.toContentValues();
                        messageValues.put(Sms.SEEN, 1);
                        messageValues.put(Sms.READ, 1);
                        messageValues.put(
                                Sms.THREAD_ID,
                                MmsSmsUtils.Threads.getOrCreateThreadId(
                                        mContext, data.getAddress()));
                        if (OsUtil.isAtLeastL_MR1()) {
                            messageValues.put(Sms.SUBSCRIPTION_ID, mSubId);
                        }
                        uri = data.isOutgoing()
                                    ? data.isUnsent()
                                            ? Sms.Outbox.CONTENT_URI
                                            : Sms.Sent.CONTENT_URI
                                    : Sms.Inbox.CONTENT_URI;
                        if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                            LogUtil.d(LogUtil.BUGLE_TAG, "Copy SMS in ICC to device"
                                    + ", uri=" + uri + ", message={" + messageValues + "}");
                        }

                        Uri ret = null;
                        try {
                            // Insert into telephony db.
                            ret = cr.insert(uri, messageValues);
                        } catch (IllegalArgumentException e) {
                            // Nothing to do
                        }
                        if (ret == null) {
                            failCount++;
                            LogUtil.e(LogUtil.BUGLE_TAG, "Failed to copy SMS in ICC into telephony"
                                    + ", uri=" + uri + ", message={" + messageValues + "}");
                        }
                    }
                }
            }
            return failCount;
        }

        @Override
        protected void onPostExecute(Integer failCount) {
            String toastMessage;
            if (failCount == 0) { // Success
                toastMessage = mMode == MODE_COPY
                                    ? mContext.getString(R.string.success_copy_sim_card_messages)
                                    : mContext.getString(R.string.success_delete_sim_card_messages);
            } else { // Fail
                toastMessage = mContext.getResources()
                                    .getQuantityString(
                                            mMode == MODE_COPY
                                                    ? R.plurals.fail_copy_sim_card_messages
                                                    : R.plurals.fail_delete_sim_card_messages,
                                            failCount,
                                            failCount);
            }
            UiUtils.showToastAtBottom(toastMessage);

            ((SimCardMessageListActivity) mContext).exitMultiSelectionMode();
        }
    }
}
