
package com.android.email;

import com.android.email.mail.Message;
import com.android.email.provider.EmailContent;

import android.content.Context;

/**
 * Defines the interface that MessagingController will use to callback to requesters. This class
 * is defined as non-abstract so that someone who wants to receive only a few messages can
 * do so without implementing the entire interface. It is highly recommended that users of
 * this interface use the @Override annotation in their implementations to avoid being caught by
 * changes in this class.
 */
public class MessagingListener {
    public void listFoldersStarted(long accountId) {
    }

    public void listFoldersFailed(long accountId, String message) {
    }

    public void listFoldersFinished(long accountId) {
    }

    public void synchronizeMailboxStarted(long accountId, long mailboxId)
            {
    }

    public void synchronizeMailboxFinished(long accountId,
            long mailboxId, int totalMessagesInMailbox, int numNewMessages) {
    }

    public void synchronizeMailboxFailed(long accountId, long mailboxId,
            Exception e) {
    }

    public void loadMessageForViewStarted(long messageId) {
    }

    public void loadMessageForViewFinished(long messageId) {
    }

    public void loadMessageForViewFailed(long messageId, String message) {
    }

    public void checkMailStarted(Context context, long accountId, long tag) {
    }

    public void checkMailFinished(Context context, long accountId, long mailboxId, long tag) {
    }

    public void sendPendingMessagesStarted(long accountId, long messageId) {
    }

    public void sendPendingMessagesCompleted(long accountId) {
    }

    public void sendPendingMessagesFailed(long accountId, long messageId, Exception reason) {
    }

    public void messageUidChanged(long accountId, long mailboxId, String oldUid, String newUid) {
    }

    public void loadAttachmentStarted(
            long accountId,
            long messageId,
            long attachmentId,
            boolean requiresDownload) {
    }

    public void loadAttachmentFinished(
            long accountId,
            long messageId,
            long attachmentId) {
    }

    public void loadAttachmentFailed(
            long accountId,
            long messageId,
            long attachmentId,
            String reason) {
    }

    /**
     * General notification messages subclasses can override to be notified that the controller
     * has completed a command. This is useful for turning off progress indicators that may have
     * been left over from previous commands.
     * @param moreCommandsToRun True if the controller will continue on to another command
     * immediately.
     */
    public void controllerCommandCompleted(boolean moreCommandsToRun) {

    }
}
