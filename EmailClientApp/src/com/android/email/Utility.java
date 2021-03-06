

package com.android.email;

import com.android.email.provider.EmailContent;
import com.android.email.provider.EmailContent.Account;
import com.android.email.provider.EmailContent.AccountColumns;
import com.android.email.provider.EmailContent.HostAuth;
import com.android.email.provider.EmailContent.HostAuthColumns;
import com.android.email.provider.EmailContent.Mailbox;
import com.android.email.provider.EmailContent.MailboxColumns;
import com.android.email.provider.EmailContent.Message;
import com.android.email.provider.EmailContent.MessageColumns;

import android.content.ContentResolver;
import android.database.Cursor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import com.android.email.codec.binary.Base64;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.widget.TextView;

public class Utility {
    public final static String readInputStream(InputStream in, String encoding) throws IOException {
        InputStreamReader reader = new InputStreamReader(in, encoding);
        StringBuffer sb = new StringBuffer();
        int count;
        char[] buf = new char[512];
        while ((count = reader.read(buf)) != -1) {
            sb.append(buf, 0, count);
        }
        return sb.toString();
    }

    public final static boolean arrayContains(Object[] a, Object o) {
        for (int i = 0, count = a.length; i < count; i++) {
            if (a[i].equals(o)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combines the given array of Objects into a single string using the
     * seperator character and each Object's toString() method. between each
     * part.
     *
     * @param parts
     * @param seperator
     * @return
     */
    public static String combine(Object[] parts, char seperator) {
        if (parts == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < parts.length; i++) {
            sb.append(parts[i].toString());
            if (i < parts.length - 1) {
                sb.append(seperator);
            }
        }
        return sb.toString();
    }

    public static String base64Decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        byte[] decoded = new Base64().decode(encoded.getBytes());
        return new String(decoded);
    }

    public static String base64Encode(String s) {
        if (s == null) {
            return s;
        }
        byte[] encoded = new Base64().encode(s.getBytes());
        return new String(encoded);
    }

    public static boolean requiredFieldValid(TextView view) {
        return view.getText() != null && view.getText().length() > 0;
    }

    public static boolean requiredFieldValid(Editable s) {
        return s != null && s.length() > 0;
    }

    /**
     * Ensures that the given string starts and ends with the double quote character. The string is not modified in any way except to add the
     * double quote character to start and end if it's not already there.
     * 
     * TODO: Rename this, because "quoteString()" can mean so many different things.
     * 
     * sample -> "sample"
     * "sample" -> "sample"
     * ""sample"" -> "sample"
     * "sample"" -> "sample"
     * sa"mp"le -> "sa"mp"le"
     * "sa"mp"le" -> "sa"mp"le"
     * (empty string) -> ""
     * " -> ""
     * @param s
     * @return
     */
    public static String quoteString(String s) {
        if (s == null) {
            return null;
        }
        if (!s.matches("^\".*\"$")) {
            return "\"" + s + "\"";
        }
        else {
            return s;
        }
    }
    
    /**
     * Apply quoting rules per IMAP RFC, 
     * quoted          = DQUOTE *QUOTED-CHAR DQUOTE
     * QUOTED-CHAR     = <any TEXT-CHAR except quoted-specials> / "\" quoted-specials
     * quoted-specials = DQUOTE / "\"
     * 
     * This is used primarily for IMAP login, but might be useful elsewhere.
     * 
     * NOTE:  Not very efficient - you may wish to preflight this, or perhaps it should check
     * for trouble chars before calling the replace functions.
     * 
     * @param s The string to be quoted.
     * @return A copy of the string, having undergone quoting as described above
     */
    public static String imapQuoted(String s) {
        
        // First, quote any backslashes by replacing \ with \\
        // regex Pattern:  \\    (Java string const = \\\\)
        // Substitute:     \\\\  (Java string const = \\\\\\\\)
        String result = s.replaceAll("\\\\", "\\\\\\\\");
        
        // Then, quote any double-quotes by replacing " with \"
        // regex Pattern:  "    (Java string const = \")
        // Substitute:     \\"  (Java string const = \\\\\")
        result = result.replaceAll("\"", "\\\\\"");
        
        // return string with quotes around it
        return "\"" + result + "\"";
    }
    
    /**
     * A fast version of  URLDecoder.decode() that works only with UTF-8 and does only two
     * allocations. This version is around 3x as fast as the standard one and I'm using it
     * hundreds of times in places that slow down the UI, so it helps.
     */
    public static String fastUrlDecode(String s) {
        try {
            byte[] bytes = s.getBytes("UTF-8");
            byte ch;
            int length = 0;
            for (int i = 0, count = bytes.length; i < count; i++) {
                ch = bytes[i];
                if (ch == '%') {
                    int h = (bytes[i + 1] - '0');
                    int l = (bytes[i + 2] - '0');
                    if (h > 9) {
                        h -= 7;
                    }
                    if (l > 9) {
                        l -= 7;
                    }
                    bytes[length] = (byte) ((h << 4) | l);
                    i += 2;
                }
                else if (ch == '+') {
                    bytes[length] = ' ';
                }
                else {
                    bytes[length] = bytes[i];
                }
                length++;
            }
            return new String(bytes, 0, length, "UTF-8");
        }
        catch (UnsupportedEncodingException uee) {
            return null;
        }
    }

    /**
     * Returns true if the specified date is within today. Returns false otherwise.
     * @param date
     * @return
     */
    public static boolean isDateToday(Date date) {
        // TODO But Calendar is so slowwwwwww....
        Date today = new Date();
        if (date.getYear() == today.getYear() &&
                date.getMonth() == today.getMonth() &&
                date.getDate() == today.getDate()) {
            return true;
        }
        return false;
    }

    /*
     * TODO disabled this method globally. It is used in all the settings screens but I just
     * noticed that an unrelated icon was dimmed. Android must share drawables internally.
     */
    public static void setCompoundDrawablesAlpha(TextView view, int alpha) {
//        Drawable[] drawables = view.getCompoundDrawables();
//        for (Drawable drawable : drawables) {
//            if (drawable != null) {
//                drawable.setAlpha(alpha);
//            }
//        }
    }

    // TODO: unit test this
    public static String buildMailboxIdSelection(ContentResolver resolver, long mailboxId) {
        // Setup default selection & args, then add to it as necessary
        StringBuilder selection = new StringBuilder(
                MessageColumns.FLAG_LOADED + " IN ("
                + Message.FLAG_LOADED_PARTIAL + "," + Message.FLAG_LOADED_COMPLETE
                + ") AND ");
        if (mailboxId == Mailbox.QUERY_ALL_INBOXES
            || mailboxId == Mailbox.QUERY_ALL_DRAFTS
            || mailboxId == Mailbox.QUERY_ALL_OUTBOX) {
            // query for all mailboxes of type INBOX, DRAFTS, or OUTBOX
            int type;
            if (mailboxId == Mailbox.QUERY_ALL_INBOXES) {
                type = Mailbox.TYPE_INBOX;
            } else if (mailboxId == Mailbox.QUERY_ALL_DRAFTS) {
                type = Mailbox.TYPE_DRAFTS;
            } else {
                type = Mailbox.TYPE_OUTBOX;
            }
            StringBuilder inboxes = new StringBuilder();
            Cursor c = resolver.query(Mailbox.CONTENT_URI,
                        EmailContent.ID_PROJECTION,
                        MailboxColumns.TYPE + "=? AND " + MailboxColumns.FLAG_VISIBLE + "=1",
                        new String[] { Integer.toString(type) }, null);
            // build an IN (mailboxId, ...) list
            // TODO do this directly in the provider
            while (c.moveToNext()) {
                if (inboxes.length() != 0) {
                    inboxes.append(",");
                }
                inboxes.append(c.getLong(EmailContent.ID_PROJECTION_COLUMN));
            }
            c.close();
            selection.append(MessageColumns.MAILBOX_KEY + " IN ");
            selection.append("(").append(inboxes).append(")");
        } else  if (mailboxId == Mailbox.QUERY_ALL_UNREAD) {
            selection.append(Message.FLAG_READ + "=0");
        } else if (mailboxId == Mailbox.QUERY_ALL_FAVORITES) {
            selection.append(Message.FLAG_FAVORITE + "=1");
        } else {
            selection.append(MessageColumns.MAILBOX_KEY + "=" + mailboxId);
        }
        return selection.toString();
    }

    public static class FolderProperties {

        private static FolderProperties sInstance;

        // Caches for frequently accessed resources.
        private String[] mSpecialMailbox = new String[] {};
        private TypedArray mSpecialMailboxDrawable;
        private Drawable mDefaultMailboxDrawable;
        private Drawable mSummaryStarredMailboxDrawable;
        private Drawable mSummaryCombinedInboxDrawable;

        private FolderProperties(Context context) {
            mSpecialMailbox = context.getResources().getStringArray(R.array.mailbox_display_names);
            for (int i = 0; i < mSpecialMailbox.length; ++i) {
                if ("".equals(mSpecialMailbox[i])) {
                    // there is no localized name, so use the display name from the server
                    mSpecialMailbox[i] = null;
                }
            }
            mSpecialMailboxDrawable =
                context.getResources().obtainTypedArray(R.array.mailbox_display_icons);
            mDefaultMailboxDrawable =
                context.getResources().getDrawable(R.drawable.ic_list_folder);
            mSummaryStarredMailboxDrawable =
                context.getResources().getDrawable(R.drawable.ic_list_starred);
            mSummaryCombinedInboxDrawable =
                context.getResources().getDrawable(R.drawable.ic_list_combined_inbox);
        }

        public static FolderProperties getInstance(Context context) {
            if (sInstance == null) {
                synchronized (FolderProperties.class) {
                    if (sInstance == null) {
                        sInstance = new FolderProperties(context);
                    }
                }
            }
            return sInstance;
        }

        /**
         * Lookup names of localized special mailboxes
         * @param type
         * @return Localized strings
         */
        public String getDisplayName(int type) {
            if (type < mSpecialMailbox.length) {
                return mSpecialMailbox[type];
            }
            return null;
        }

        /**
         * Lookup icons of special mailboxes
         * @param type
         * @return icon's drawable
         */
        public Drawable getIconIds(int type) {
            if (type < mSpecialMailboxDrawable.length()) {
                return mSpecialMailboxDrawable.getDrawable(type);
            }
            return mDefaultMailboxDrawable;
        }

        public Drawable getSummaryMailboxIconIds(long mailboxKey) {
            if (mailboxKey == Mailbox.QUERY_ALL_INBOXES) {
                return mSummaryCombinedInboxDrawable;
            } else if (mailboxKey == Mailbox.QUERY_ALL_FAVORITES) {
                return mSummaryStarredMailboxDrawable;
            } else if (mailboxKey == Mailbox.QUERY_ALL_DRAFTS) {
                return mSpecialMailboxDrawable.getDrawable(Mailbox.TYPE_DRAFTS);
            } else if (mailboxKey == Mailbox.QUERY_ALL_OUTBOX) {
                return mSpecialMailboxDrawable.getDrawable(Mailbox.TYPE_OUTBOX);
            }
            return mDefaultMailboxDrawable;
        }
    }

    private final static String HOSTAUTH_WHERE_CREDENTIALS = HostAuthColumns.ADDRESS + " like ?"
            + " and " + HostAuthColumns.LOGIN + " like ?"
            + " and " + HostAuthColumns.PROTOCOL + " not like \"smtp\"";
    private final static String ACCOUNT_WHERE_HOSTAUTH = AccountColumns.HOST_AUTH_KEY_RECV + "=?";

    /**
     * Look for an existing account with the same username & server
     *
     * @param context a system context
     * @param allowAccountId this account Id will not trigger (when editing an existing account)
     * @param hostName the server
     * @param userLogin the user login string
     * @result null = no dupes found.  non-null = dupe account's display name
     */
    public static String findDuplicateAccount(Context context, long allowAccountId, String hostName,
            String userLogin) {
        ContentResolver resolver = context.getContentResolver();
        Cursor c = resolver.query(HostAuth.CONTENT_URI, HostAuth.ID_PROJECTION,
                HOSTAUTH_WHERE_CREDENTIALS, new String[] { hostName, userLogin }, null);
        try {
            while (c.moveToNext()) {
                long hostAuthId = c.getLong(HostAuth.ID_PROJECTION_COLUMN);
                // Find account with matching hostauthrecv key, and return its display name
                Cursor c2 = resolver.query(Account.CONTENT_URI, Account.ID_PROJECTION,
                        ACCOUNT_WHERE_HOSTAUTH, new String[] { Long.toString(hostAuthId) }, null);
                try {
                    while (c2.moveToNext()) {
                        long accountId = c2.getLong(Account.ID_PROJECTION_COLUMN);
                        if (accountId != allowAccountId) {
                            Account account = Account.restoreAccountWithId(context, accountId);
                            if (account != null) {
                                return account.mDisplayName;
                            }
                        }
                    }
                } finally {
                    c2.close();
                }
            }
        } finally {
            c.close();
        }

        return null;
    }

    /**
     * Generate a random message-id header for locally-generated messages.
     */
    public static String generateMessageId() {
        StringBuffer sb = new StringBuffer();
        sb.append("<");
        for (int i = 0; i < 24; i++) {
            sb.append(Integer.toString((int)(Math.random() * 35), 36));
        }
        sb.append(".");
        sb.append(Long.toString(System.currentTimeMillis()));
        sb.append("@email.android.com>");
        return sb.toString();
    }

}
