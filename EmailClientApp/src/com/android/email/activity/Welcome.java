
package com.android.email.activity;

import com.android.email.activity.setup.AccountSetupBasics;
import com.android.email.provider.EmailContent.Account;
import com.android.email.provider.EmailContent.Mailbox;
import com.android.exchange.SyncManager;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

/**
 * The Welcome activity initializes the application and decides what Activity
 * the user should start with.
 * If no accounts are configured the user is taken to the AccountSetupBasics Activity where they
 * can configure an account.
 * If a single account is configured the user is taken directly to the MessageList for
 * the INBOX of that account.
 * If more than one account is configured the user is taken to the AccountFolderList Activity so
 * they can select an account.
 */
public class Welcome extends Activity {

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Because the app could be reloaded (for debugging, etc.), we need to make sure that
        // SyncManager gets a chance to start.  There is no harm to starting it if it has already
        // been started
        // TODO More completely separate SyncManager from Email app
        startService(new Intent(this, SyncManager.class));

        // Find out how many accounts we have, and if there's just one, go directly to it
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    Account.CONTENT_URI,
                    Account.ID_PROJECTION,
                    null, null, null);
            switch (c.getCount()) {
                case 0:
                    AccountSetupBasics.actionNewAccount(this);
                    break;
                case 1:
                	//milochen change after disable accounts button on MessageList, 
                	//So MessageList cannot go to AccountFolderList, so we need to defaultly go to AccountFolderList.
//                    c.moveToFirst();
//                    long accountId = c.getLong(Account.CONTENT_ID_COLUMN);
//                    MessageList.actionHandleAccount(this, accountId, Mailbox.TYPE_INBOX);
                	  AccountFolderList.actionShowAccounts(this);
                    break;
                default:
                    AccountFolderList.actionShowAccounts(this);
                    break;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        // In all cases, do not return to this activity
        finish();
    }
}
