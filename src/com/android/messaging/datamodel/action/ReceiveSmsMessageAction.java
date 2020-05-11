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

package com.android.messaging.datamodel.action;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony.Sms;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.SyncManager;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;

/**
 * Action used to "receive" an incoming message
 */
public class ReceiveSmsMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    private static final String KEY_MESSAGE_VALUES = "message_values";
    private static final String KEY_SUB_ID = "sub_id";
    private static final String KEY_IS_REPLACE_SMS = "is_replace_sms";

    /**
     * Create a message received from a particular number in a particular conversation
     */
    public ReceiveSmsMessageAction(final ContentValues messageValues, final boolean isReplaceSms) {
        actionParameters.putParcelable(KEY_MESSAGE_VALUES, messageValues);
        actionParameters.putBoolean(KEY_IS_REPLACE_SMS, isReplaceSms);
    }

    @Override
    protected Object executeAction() {
        final Context context = Factory.get().getApplicationContext();
        final ContentValues messageValues = actionParameters.getParcelable(KEY_MESSAGE_VALUES);
        final boolean isReplaceSms = actionParameters.getBoolean(KEY_IS_REPLACE_SMS);
        final DatabaseWrapper db = DataModel.get().getDatabase();

        // Get the SIM subscription ID
        Integer subId = messageValues.getAsInteger(Sms.SUBSCRIPTION_ID);
        if (subId == null) {
            subId = ParticipantData.DEFAULT_SELF_SUB_ID;
        }
        // Make sure we have a sender address
        String address = messageValues.getAsString(Sms.ADDRESS);
        if (TextUtils.isEmpty(address)) {
            LogUtil.w(TAG, "Received an SMS without an address; using unknown sender.");
            address = ParticipantData.getUnknownSenderDestination();
            messageValues.put(Sms.ADDRESS, address);
        }
        final ParticipantData rawSender = ParticipantData.getFromRawPhoneBySimLocale(
                address, subId);

        // TODO: Should use local timestamp for this?
        final long received = messageValues.getAsLong(Sms.DATE);
        // Inform sync that message has been added at local received timestamp
        final SyncManager syncManager = DataModel.get().getSyncManager();
        syncManager.onNewMessageInserted(received);

        // Make sure we've got a thread id
        final long threadId = MmsSmsUtils.Threads.getOrCreateThreadId(context, address);
        messageValues.put(Sms.THREAD_ID, threadId);
        final boolean blocked = BugleDatabaseOperations.isBlockedDestination(
                db, rawSender.getNormalizedDestination());
        final String conversationId = BugleDatabaseOperations.
                getOrCreateConversationFromRecipient(db, threadId, blocked, rawSender);

        final boolean messageInFocusedConversation =
                DataModel.get().isFocusedConversation(conversationId);
        final boolean messageInObservableConversation =
                DataModel.get().isNewMessageObservable(conversationId);

        MessageData message = null;
        // Only the primary user gets to insert the message into the telephony db and into bugle's
        // db. The secondary user goes through this path, but skips doing the actual insert. It
        // goes through this path because it needs to compute messageInFocusedConversation in order
        // to calculate whether to skip the notification and play a soft sound if the user is
        // already in the conversation.
        if (!OsUtil.isSecondaryUser()) {
            final boolean read = messageValues.getAsBoolean(Sms.Inbox.READ)
                    || messageInFocusedConversation;
            // If you have read it you have seen it
            final boolean seen = read || messageInObservableConversation || blocked;
            messageValues.put(Sms.Inbox.READ, read ? Integer.valueOf(1) : Integer.valueOf(0));

            // incoming messages are marked as seen in the telephony db
            messageValues.put(Sms.Inbox.SEEN, 1);

            Uri messageUri = null;
            final ContentResolver cr = context.getContentResolver();
            boolean hasReplaced = false;
            if (isReplaceSms) {
                LogUtil.d(TAG, "ReceiveSmsMessageAction: Received new Replace Short Message");
                final String protocol = messageValues.getAsString(Sms.PROTOCOL);
                String selection = Sms.ADDRESS + "=? AND " + Sms.PROTOCOL + "=?";
                String[] selectionArgs = null;
                if (OsUtil.isAtLeastL_MR1()) {
                    selection += " AND " + Sms.SUBSCRIPTION_ID + "=?";
                    selectionArgs = new String[] {address, protocol, String.valueOf(subId)};
                } else {
                    selectionArgs = new String[] {address, protocol};
                }
                // Replace with new message in telephony db
                if (cr.update(Sms.Inbox.CONTENT_URI, messageValues, selection, selectionArgs) > 0) {
                    try (Cursor c =
                            cr.query(
                                    Sms.Inbox.CONTENT_URI,
                                    new String[] {Sms._ID},
                                    selection,
                                    selectionArgs,
                                    null)) {
                        if (c != null && c.moveToFirst()) {
                            // Use Sms.CONTENT_URI instead since SmsProvider returns an uri based on
                            // Sms.CONTENT_URI for insert operation always now.
                            messageUri = ContentUris.withAppendedId(Sms.CONTENT_URI, c.getLong(0));
                            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                                LogUtil.d(TAG, "ReceiveSmsMessageAction: Updated SMS message uri="
                                        + messageUri + " in telephony");
                            }
                            hasReplaced = true;
                        }
                    }
                }
            }

            if (messageUri == null) {
                // Insert new incoming message into telephony
                messageUri = cr.insert(Sms.Inbox.CONTENT_URI, messageValues);
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "ReceiveSmsMessageAction: Inserted SMS message into telephony, "
                            + "uri = " + messageUri);
                }
            }

            if (messageUri == null) {
                LogUtil.e(TAG, "ReceiveSmsMessageAction: Failed to insert SMS into telephony!");
            }

            final String text = messageValues.getAsString(Sms.BODY);
            final String subject = messageValues.getAsString(Sms.SUBJECT);
            final long sent = messageValues.getAsLong(Sms.DATE_SENT);
            final ParticipantData self = ParticipantData.getSelfParticipant(subId);
            final Integer pathPresent = messageValues.getAsInteger(Sms.REPLY_PATH_PRESENT);
            final String smsServiceCenter = messageValues.getAsString(Sms.SERVICE_CENTER);
            String conversationServiceCenter = null;
            // Only set service center if message REPLY_PATH_PRESENT = 1
            if (pathPresent != null && pathPresent == 1 && !TextUtils.isEmpty(smsServiceCenter)) {
                conversationServiceCenter = smsServiceCenter;
            }
            db.beginTransaction();
            try {
                final String participantId =
                        BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, rawSender);
                if (hasReplaced) {
                    message = BugleDatabaseOperations.readMessage(db, messageUri);
                    if (message != null) {
                        final ContentValues values = new ContentValues();
                        values.put(DatabaseHelper.MessageColumns.MMS_SUBJECT, subject);
                        values.put(DatabaseHelper.MessageColumns.SENT_TIMESTAMP, sent);
                        values.put(DatabaseHelper.MessageColumns.RECEIVED_TIMESTAMP, received);
                        values.put(DatabaseHelper.MessageColumns.SEEN, seen);
                        values.put(DatabaseHelper.MessageColumns.READ, read);
                        BugleDatabaseOperations.updateMessageRowIfExists(
                                db, message.getMessageId(), values);
                        values.clear();
                        for (final MessagePartData part : message.getParts()) {
                            values.put(DatabaseHelper.PartColumns.TEXT, text);
                            BugleDatabaseOperations.updatePartRowIfExists(
                                    db, part.getPartId(), values);
                        }
                    }
                } else {
                    final String selfId =
                            BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, self);

                    message = MessageData.createReceivedSmsMessage(messageUri, conversationId,
                            participantId, selfId, text, subject, sent, received, seen, read);

                    BugleDatabaseOperations.insertNewMessageInTransaction(db, message);
                }

                BugleDatabaseOperations.updateConversationMetadataInTransaction(db, conversationId,
                        message.getMessageId(), message.getReceivedTimeStamp(), blocked,
                        conversationServiceCenter, true /* shouldAutoSwitchSelfId */);

                final ParticipantData sender = ParticipantData.getFromId(db, participantId);
                BugleActionToasts.onMessageReceived(conversationId, sender, message);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            LogUtil.i(TAG, "ReceiveSmsMessageAction: Received SMS message " + message.getMessageId()
                    + " in conversation " + message.getConversationId()
                    + ", uri = " + messageUri);

            actionParameters.putInt(KEY_SUB_ID, subId);
            ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(false, this);
        } else {
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "ReceiveSmsMessageAction: Not inserting received SMS message for "
                        + "secondary user.");
            }
        }
        // Show a notification to let the user know a new message has arrived
        BugleNotifications.update(false/*silent*/, conversationId, BugleNotifications.UPDATE_ALL);

        MessagingContentProvider.notifyMessagesChanged(conversationId);
        MessagingContentProvider.notifyPartsChanged();

        return message;
    }

    private ReceiveSmsMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ReceiveSmsMessageAction> CREATOR
            = new Parcelable.Creator<ReceiveSmsMessageAction>() {
        @Override
        public ReceiveSmsMessageAction createFromParcel(final Parcel in) {
            return new ReceiveSmsMessageAction(in);
        }

        @Override
        public ReceiveSmsMessageAction[] newArray(final int size) {
            return new ReceiveSmsMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
