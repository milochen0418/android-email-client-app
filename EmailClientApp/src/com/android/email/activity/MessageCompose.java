

package com.android.email.activity;

import com.android.email.Controller;
import com.android.email.Email;
import com.android.email.EmailAddressAdapter;
import com.android.email.EmailAddressValidator;
import com.android.email.R;
import com.android.email.Utility;
import com.android.email.mail.Address;
import com.android.email.mail.MessagingException;
import com.android.email.mail.internet.EmailHtmlUtil;
import com.android.email.mail.internet.MimeUtility;
import com.android.email.provider.EmailContent;
import com.android.email.provider.EmailContent.Account;
import com.android.email.provider.EmailContent.Attachment;
import com.android.email.provider.EmailContent.Body;
import com.android.email.provider.EmailContent.BodyColumns;
import com.android.email.provider.EmailContent.Message;
import com.android.email.provider.EmailContent.MessageColumns;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;


public class MessageCompose extends Activity implements OnClickListener, OnFocusChangeListener {
    private static final String ACTION_REPLY = "com.android.email.intent.action.REPLY";
    private static final String ACTION_REPLY_ALL = "com.android.email.intent.action.REPLY_ALL";
    private static final String ACTION_FORWARD = "com.android.email.intent.action.FORWARD";
    private static final String ACTION_EDIT_DRAFT = "com.android.email.intent.action.EDIT_DRAFT";

    private static final String EXTRA_ACCOUNT_ID = "account_id";
    private static final String EXTRA_MESSAGE_ID = "message_id";
    private static final String STATE_KEY_CC_SHOWN =
        "com.android.email.activity.MessageCompose.ccShown";
    private static final String STATE_KEY_BCC_SHOWN =
        "com.android.email.activity.MessageCompose.bccShown";
    private static final String STATE_KEY_QUOTED_TEXT_SHOWN =
        "com.android.email.activity.MessageCompose.quotedTextShown";
    private static final String STATE_KEY_SOURCE_MESSAGE_PROCED =
        "com.android.email.activity.MessageCompose.stateKeySourceMessageProced";
    private static final String STATE_KEY_DRAFT_ID =
        "com.android.email.activity.MessageCompose.draftId";

    
    
    private static final int MSG_PROGRESS_ON = 1;
    private static final int MSG_PROGRESS_OFF = 2;
    private static final int MSG_UPDATE_TITLE = 3;
    private static final int MSG_SKIPPED_ATTACHMENTS = 4;
    private static final int MSG_DISCARDED_DRAFT = 6;

    private static final int ACTIVITY_REQUEST_PICK_ATTACHMENT = 1;    
    //milochen  add
    private static final int REQUEST_CODE_ADD_TO = 4001;
    private static final int REQUEST_CODE_ADD_CC = 4002;
    private static final int REQUEST_CODE_ADD_BCC = 4003;
    
    
    private static final String[] ATTACHMENT_META_COLUMNS = {
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE
    };

    //milochen add
    private static final int[] mOptionsButtonIds = new int[] {
    	R.id.add_cc_bcc,
    	R.id.add_attachment,
    	R.id.send,
    	R.id.save,
    	R.id.discard,
    	R.id.backkey,
    };
    
    
    
    
    private Account mAccount;

    // mDraft has mId > 0 after the first draft save.
    private Message mDraft = new Message();

    // mSource is only set for REPLY, REPLY_ALL and FORWARD, and contains the source message.
    private Message mSource;

    // we use mAction instead of Intent.getAction() because sometimes we need to
    // re-write the action to EDIT_DRAFT.
    private String mAction;

    /**
     * Indicates that the source message has been processed at least once and should not
     * be processed on any subsequent loads. This protects us from adding attachments that
     * have already been added from the restore of the view state.
     */
    private boolean mSourceMessageProcessed = false;

    private MultiAutoCompleteTextView mToView;
    private MultiAutoCompleteTextView mCcView;
    private MultiAutoCompleteTextView mBccView;
    //milochen add 
    private LinearLayout mToLayout;
    private LinearLayout mCcLayout;
    private LinearLayout mBccLayout;
    private Button mAddTo;
    private Button mAddCc;
    private Button mAddBcc;
    
    private EditText mSubjectView;
    private EditText mMessageContentView;
    private Button mSendButton;
    private Button mDiscardButton;
    private Button mSaveButton;
    private LinearLayout mAttachments;
    private View mQuotedTextBar;
    private ImageButton mQuotedTextDelete;
    private WebView mQuotedText;

    private Controller mController;
    private Listener mListener = new Listener();
    private boolean mDraftNeedsSaving;
    private boolean mMessageLoaded;
    private AsyncTask mLoadAttachmentsTask;
    private AsyncTask mSaveMessageTask;
    private AsyncTask mLoadMessageTask;

    private EmailAddressAdapter mAddressAdapter;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_PROGRESS_ON:
                    setProgressBarIndeterminateVisibility(true);
                    break;
                case MSG_PROGRESS_OFF:
                    setProgressBarIndeterminateVisibility(false);
                    break;
                case MSG_UPDATE_TITLE:
                    updateTitle();
                    break;
                case MSG_SKIPPED_ATTACHMENTS:
                    Toast.makeText(
                            MessageCompose.this,
                            getString(R.string.message_compose_attachments_skipped_toast),
                            Toast.LENGTH_LONG).show();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    /**
     * Compose a new message using the given account. If account is -1 the default account
     * will be used.
     * @param context
     * @param accountId
     */
    public static void actionCompose(Context context, long accountId) {
       try {
           Intent i = new Intent(context, MessageCompose.class);
           i.putExtra(EXTRA_ACCOUNT_ID, accountId);
           context.startActivity(i);
       } catch (ActivityNotFoundException anfe) {
           // Swallow it - this is usually a race condition, especially under automated test.
           // (The message composer might have been disabled)
           Email.log(anfe.toString());
       }
    }

    /**
     * Compose a new message using a uri (mailto:) and a given account.  If account is -1 the
     * default account will be used.
     * @param context
     * @param uriString
     * @param accountId
     * @return true if startActivity() succeeded
     */
    public static boolean actionCompose(Context context, String uriString, long accountId) {
        try {
            Intent i = new Intent(context, MessageCompose.class);
            i.setAction(Intent.ACTION_SEND);
            i.setData(Uri.parse(uriString));
            i.putExtra(EXTRA_ACCOUNT_ID, accountId);
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException anfe) {
            // Swallow it - this is usually a race condition, especially under automated test.
            // (The message composer might have been disabled)
            Email.log(anfe.toString());
            return false;
        }
    }

    /**
     * Compose a new message as a reply to the given message. If replyAll is true the function
     * is reply all instead of simply reply.
     * @param context
     * @param messageId
     * @param replyAll
     */
    public static void actionReply(Context context, long messageId, boolean replyAll) {
        startActivityWithMessage(context, replyAll ? ACTION_REPLY_ALL : ACTION_REPLY, messageId);
    }

    /**
     * Compose a new message as a forward of the given message.
     * @param context
     * @param messageId
     */
    public static void actionForward(Context context, long messageId) {
        startActivityWithMessage(context, ACTION_FORWARD, messageId);
    }

    /**
     * Continue composition of the given message. This action modifies the way this Activity
     * handles certain actions.
     * Save will attempt to replace the message in the given folder with the updated version.
     * Discard will delete the message from the given folder.
     * @param context
     * @param messageId the message id.
     */
    public static void actionEditDraft(Context context, long messageId) {
        startActivityWithMessage(context, ACTION_EDIT_DRAFT, messageId);
    }

    private static void startActivityWithMessage(Context context, String action, long messageId) {
        Intent i = new Intent(context, MessageCompose.class);
        i.putExtra(EXTRA_MESSAGE_ID, messageId);
        i.setAction(action);
        context.startActivity(i);
    }

    private void setAccount(Intent intent) {
        long accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1);
        if (accountId == -1) {
            accountId = Account.getDefaultAccountId(this);
        }
        if (accountId == -1) {
            // There are no accounts set up. This should not have happened. Prompt the
            // user to set up an account as an acceptable bailout.
            AccountFolderList.actionShowAccounts(this);
            finish();
        } else {
            mAccount = Account.restoreAccountWithId(this, accountId);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("EMAIL","MessageCompose.onCreate(savedInstanceState); arg=" + savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.message_compose);
        mController = Controller.getInstance(getApplication());
        initViews();

        long draftId = -1;
        if (savedInstanceState != null) {
            // This data gets used in onCreate, so grab it here instead of onRestoreIntstanceState
            mSourceMessageProcessed =
                savedInstanceState.getBoolean(STATE_KEY_SOURCE_MESSAGE_PROCED, false);
            draftId = savedInstanceState.getLong(STATE_KEY_DRAFT_ID, -1);
        }

        Intent intent = getIntent();
        mAction = intent.getAction();

        if (draftId != -1) {
            // this means that we saved the draft earlier,
            // so now we need to disregard the intent action and do
            // EDIT_DRAFT instead.
            mAction = ACTION_EDIT_DRAFT;
            mDraft.mId = draftId;
        }

        // Handle the various intents that launch the message composer
        if (Intent.ACTION_VIEW.equals(mAction)
                || Intent.ACTION_SENDTO.equals(mAction)
                || Intent.ACTION_SEND.equals(mAction)
                || Intent.ACTION_SEND_MULTIPLE.equals(mAction)) {
            setAccount(intent);
            // Use the fields found in the Intent to prefill as much of the message as possible
            initFromIntent(intent);
            mDraftNeedsSaving = true;
            mMessageLoaded = true;
            mSourceMessageProcessed = true;
        } else {
            // Otherwise, handle the internal cases (Message Composer invoked from within app)
            long messageId = draftId != -1 ? draftId : intent.getLongExtra(EXTRA_MESSAGE_ID, -1);
            if (messageId != -1) {
                mLoadMessageTask = new LoadMessageTask().execute(messageId);
            } else {
                setAccount(intent);
                // Since this is a new message, we don't need to call LoadMessageTask.
                // But we DO need to set mMessageLoaded to indicate the message can be sent
                mMessageLoaded = true;
                mSourceMessageProcessed = true;
            }
        }

        if (ACTION_REPLY.equals(mAction) || ACTION_REPLY_ALL.equals(mAction) ||
                ACTION_FORWARD.equals(mAction) || ACTION_EDIT_DRAFT.equals(mAction)) {
            /*
             * If we need to load the message we add ourself as a message listener here
             * so we can kick it off. Normally we add in onResume but we don't
             * want to reload the message every time the activity is resumed.
             * There is no harm in adding twice.
             */
            // TODO: signal the controller to load the message
        }
        updateTitle();
    }

    // needed for unit tests
    @Override
    public void setIntent(Intent intent) {
        super.setIntent(intent);
        mAction = intent.getAction();
    }

    @Override
    public void onResume() {
        super.onResume();
        mController.addResultCallback(mListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveIfNeeded();
        mController.removeResultCallback(mListener);
    }

    private static void cancelTask(AsyncTask<?, ?, ?> task) {
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(true);
        }
    }

    /**
     * We override onDestroy to make sure that the WebView gets explicitly destroyed.
     * Otherwise it can leak native references.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mQuotedText.destroy();
        mQuotedText = null;
        cancelTask(mLoadAttachmentsTask);
        mLoadAttachmentsTask = null;
        cancelTask(mLoadMessageTask);
        mLoadMessageTask = null;
        // don't cancel mSaveMessageTask, let it do its job to the end.

        // Make sure the adapter doesn't leak its cursor
        if (mAddressAdapter != null) {
            mAddressAdapter.changeCursor(null);
        }
    }

    /**
     * The framework handles most of the fields, but we need to handle stuff that we
     * dynamically show and hide:
     * Cc field,
     * Bcc field,
     * Quoted text,
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long draftId = getOrCreateDraftId();
        if (draftId != -1) {
            outState.putLong(STATE_KEY_DRAFT_ID, draftId);
        }
        outState.putBoolean(STATE_KEY_CC_SHOWN, mCcView.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_KEY_BCC_SHOWN, mBccView.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_KEY_QUOTED_TEXT_SHOWN,
                mQuotedTextBar.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_KEY_SOURCE_MESSAGE_PROCED, mSourceMessageProcessed);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mCcView.setVisibility(savedInstanceState.getBoolean(STATE_KEY_CC_SHOWN) ?
                View.VISIBLE : View.GONE);
        mBccView.setVisibility(savedInstanceState.getBoolean(STATE_KEY_BCC_SHOWN) ?
                View.VISIBLE : View.GONE);
        mQuotedTextBar.setVisibility(savedInstanceState.getBoolean(STATE_KEY_QUOTED_TEXT_SHOWN) ?
                View.VISIBLE : View.GONE);
        mQuotedText.setVisibility(savedInstanceState.getBoolean(STATE_KEY_QUOTED_TEXT_SHOWN) ?
                View.VISIBLE : View.GONE);
        mDraftNeedsSaving = false;
    }

    private void initViews() {
        mToView = (MultiAutoCompleteTextView)findViewById(R.id.to);
        mCcView = (MultiAutoCompleteTextView)findViewById(R.id.cc);
        mBccView = (MultiAutoCompleteTextView)findViewById(R.id.bcc);
        //milochen add
        mToLayout = (LinearLayout) findViewById(R.id.to_layout);
        mCcLayout = (LinearLayout) findViewById(R.id.cc_layout);
        mBccLayout = (LinearLayout) findViewById(R.id.bcc_layout);
        mAddTo = (Button) findViewById(R.id.addTo);
        mAddCc = (Button) findViewById(R.id.addCc);
        mAddBcc = (Button) findViewById(R.id.addBcc);
        
        mSubjectView = (EditText)findViewById(R.id.subject);
        mMessageContentView = (EditText)findViewById(R.id.message_content);
        mSendButton = (Button)findViewById(R.id.send);
        mDiscardButton = (Button)findViewById(R.id.discard);
        mSaveButton = (Button)findViewById(R.id.save);
        mAttachments = (LinearLayout)findViewById(R.id.attachments);
        mQuotedTextBar = findViewById(R.id.quoted_text_bar);
        mQuotedTextDelete = (ImageButton)findViewById(R.id.quoted_text_delete);
        mQuotedText = (WebView)findViewById(R.id.quoted_text);

        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start,
                                          int before, int after) { }

            public void onTextChanged(CharSequence s, int start,
                                          int before, int count) {
                mDraftNeedsSaving = true;
            }

            public void afterTextChanged(android.text.Editable s) { }
        };

        /**
         * Implements special address cleanup rules:
         * The first space key entry following an "@" symbol that is followed by any combination
         * of letters and symbols, including one+ dots and zero commas, should insert an extra
         * comma (followed by the space).
         */
        InputFilter recipientFilter = new InputFilter() {

            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                    int dstart, int dend) {

                // quick check - did they enter a single space?
                if (end-start != 1 || source.charAt(start) != ' ') {
                    return null;
                }

                // determine if the characters before the new space fit the pattern
                // follow backwards and see if we find a comma, dot, or @
                int scanBack = dstart;
                boolean dotFound = false;
                while (scanBack > 0) {
                    char c = dest.charAt(--scanBack);
                    switch (c) {
                        case '.':
                            dotFound = true;    // one or more dots are req'd
                            break;
                        case ',':
                            return null;
                        case '@':
                            if (!dotFound) {
                                return null;
                            }

                            // we have found a comma-insert case.  now just do it
                            // in the least expensive way we can.
                            if (source instanceof Spanned) {
                                SpannableStringBuilder sb = new SpannableStringBuilder(",");
                                sb.append(source);
                                return sb;
                            } else {
                                return ", ";
                            }
                        default:
                            // just keep going
                    }
                }

                // no termination cases were found, so don't edit the input
                return null;
            }
        };
        InputFilter[] recipientFilters = new InputFilter[] { recipientFilter };

        mToView.addTextChangedListener(watcher);
        mCcView.addTextChangedListener(watcher);
        mBccView.addTextChangedListener(watcher);
        mSubjectView.addTextChangedListener(watcher);
        mMessageContentView.addTextChangedListener(watcher);

        // NOTE: assumes no other filters are set
        mToView.setFilters(recipientFilters);
        mCcView.setFilters(recipientFilters);
        mBccView.setFilters(recipientFilters);

        /*
         * We set this to invisible by default. Other methods will turn it back on if it's
         * needed.
         */
        mQuotedTextBar.setVisibility(View.GONE);
        mQuotedText.setVisibility(View.GONE);

        mQuotedTextDelete.setOnClickListener(this);

        //milochen add
        int idx;
        for (idx = 0; idx < mOptionsButtonIds.length; idx++){
        	if(null != findViewById(mOptionsButtonIds[idx])){
        		findViewById(mOptionsButtonIds[idx]).setOnClickListener(this);
        	}
         }
        mAddTo.setOnClickListener(this);
        mAddCc.setOnClickListener(this);
        mAddBcc.setOnClickListener(this);
        
        
        mAddressAdapter = new EmailAddressAdapter(this);
        EmailAddressValidator addressValidator = new EmailAddressValidator();

        mToView.setAdapter(mAddressAdapter);
        mToView.setTokenizer(new Rfc822Tokenizer());
        mToView.setValidator(addressValidator);

        mCcView.setAdapter(mAddressAdapter);
        mCcView.setTokenizer(new Rfc822Tokenizer());
        mCcView.setValidator(addressValidator);

        mBccView.setAdapter(mAddressAdapter);
        mBccView.setTokenizer(new Rfc822Tokenizer());
        mBccView.setValidator(addressValidator);

        mSendButton.setOnClickListener(this);
        mDiscardButton.setOnClickListener(this);
        mSaveButton.setOnClickListener(this);

        mSubjectView.setOnFocusChangeListener(this);
    }

    // TODO: is there any way to unify this with MessageView.LoadMessageTask?
    private class LoadMessageTask extends AsyncTask<Long, Void, Object[]> {
        @Override
        protected Object[] doInBackground(Long... messageIds) {
            Message message = Message.restoreMessageWithId(MessageCompose.this, messageIds[0]);
            if (message == null) {
                return new Object[] {null, null};
            }
            long accountId = message.mAccountKey;
            Account account = Account.restoreAccountWithId(MessageCompose.this, accountId);
            try {
                // Body body = Body.restoreBodyWithMessageId(MessageCompose.this, message.mId);
                message.mHtml = Body.restoreBodyHtmlWithMessageId(MessageCompose.this, message.mId);
                message.mText = Body.restoreBodyTextWithMessageId(MessageCompose.this, message.mId);
                boolean isEditDraft = ACTION_EDIT_DRAFT.equals(mAction);
                // the reply fields are only filled/used for Drafts.
                if (isEditDraft) {
                    message.mHtmlReply =
                        Body.restoreReplyHtmlWithMessageId(MessageCompose.this, message.mId);
                    message.mTextReply =
                        Body.restoreReplyTextWithMessageId(MessageCompose.this, message.mId);
                    message.mIntroText =
                        Body.restoreIntroTextWithMessageId(MessageCompose.this, message.mId);
                } else {
                    message.mHtmlReply = null;
                    message.mTextReply = null;
                    message.mIntroText = null;
                }
            } catch (RuntimeException e) {
                Log.d(Email.LOG_TAG, "Exception while loading message body: " + e);
                return new Object[] {null, null};
            }
            return new Object[]{message, account};
        }

        @Override
        protected void onPostExecute(Object[] messageAndAccount) {
            if (messageAndAccount == null) {
                return;
            }

            final Message message = (Message) messageAndAccount[0];
            final Account account = (Account) messageAndAccount[1];
            if (message == null && account == null) {
                // Something unexpected happened:
                // the message or the body couldn't be loaded by SQLite.
                // Bail out.
                Toast.makeText(MessageCompose.this, R.string.error_loading_message_body,
                               Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            if (ACTION_EDIT_DRAFT.equals(mAction)) {
                mDraft = message;
                mLoadAttachmentsTask = new AsyncTask<Long, Void, Attachment[]>() {
                    @Override
                    protected Attachment[] doInBackground(Long... messageIds) {
                        return Attachment.restoreAttachmentsWithMessageId(MessageCompose.this,
                                messageIds[0]);
                    }
                    @Override
                    protected void onPostExecute(Attachment[] attachments) {
                        if (attachments == null) {
                            return;
                        }
                        for (Attachment attachment : attachments) {
                            addAttachment(attachment);
                        }
                    }
                }.execute(message.mId);
            } else if (ACTION_REPLY.equals(mAction)
                       || ACTION_REPLY_ALL.equals(mAction)
                       || ACTION_FORWARD.equals(mAction)) {
                mSource = message;
            } else if (Email.LOGD) {
                Email.log("Action " + mAction + " has unexpected EXTRA_MESSAGE_ID");
            }

            mAccount = account;
            processSourceMessageGuarded(message, mAccount);
            mMessageLoaded = true;
        }
    }

    private void updateTitle() {
        if (mSubjectView.getText().length() == 0) {
            setTitle(R.string.compose_title);
        } else {
            setTitle(mSubjectView.getText().toString());
        }
    }

    public void onFocusChange(View view, boolean focused) {
        if (!focused) {
            updateTitle();
        }
    }

    private void addAddresses(MultiAutoCompleteTextView view, Address[] addresses) {
        if (addresses == null) {
            return;
        }
        for (Address address : addresses) {
            addAddress(view, address.toString());
        }
    }

    private void addAddresses(MultiAutoCompleteTextView view, String[] addresses) {
        if (addresses == null) {
            return;
        }
        for (String oneAddress : addresses) {
            addAddress(view, oneAddress);
        }
    }

    private void addAddress(MultiAutoCompleteTextView view, String address) {
        view.append(address + ", ");
    }

    private String getPackedAddresses(TextView view) {
        Address[] addresses = Address.parse(view.getText().toString().trim());
        return Address.pack(addresses);
    }

    private Address[] getAddresses(TextView view) {
        Address[] addresses = Address.parse(view.getText().toString().trim());
        return addresses;
    }

    /*
     * Computes a short string indicating the destination of the message based on To, Cc, Bcc.
     * If only one address appears, returns the friendly form of that address.
     * Otherwise returns the friendly form of the first address appended with "and N others".
     */
    private String makeDisplayName(String packedTo, String packedCc, String packedBcc) {
        Address first = null;
        int nRecipients = 0;
        for (String packed: new String[] {packedTo, packedCc, packedBcc}) {
            Address[] addresses = Address.unpack(packed);
            nRecipients += addresses.length;
            if (first == null && addresses.length > 0) {
                first = addresses[0];
            }
        }
        if (nRecipients == 0) {
            return "";
        }
        String friendly = first.toFriendly();
        if (nRecipients == 1) {
            return friendly;
        }
        return this.getString(R.string.message_compose_display_name, friendly, nRecipients - 1);
    }

    private ContentValues getUpdateContentValues(Message message) {
        ContentValues values = new ContentValues();
        values.put(MessageColumns.TIMESTAMP, message.mTimeStamp);
        values.put(MessageColumns.FROM_LIST, message.mFrom);
        values.put(MessageColumns.TO_LIST, message.mTo);
        values.put(MessageColumns.CC_LIST, message.mCc);
        values.put(MessageColumns.BCC_LIST, message.mBcc);
        values.put(MessageColumns.SUBJECT, message.mSubject);
        values.put(MessageColumns.DISPLAY_NAME, message.mDisplayName);
        values.put(MessageColumns.FLAG_READ, message.mFlagRead);
        values.put(MessageColumns.FLAG_LOADED, message.mFlagLoaded);
        values.put(MessageColumns.FLAG_ATTACHMENT, message.mFlagAttachment);
        values.put(MessageColumns.FLAGS, message.mFlags);
        return values;
    }

    /**
     * @param message The message to be updated.
     * @param account the account (used to obtain From: address).
     * @param bodyText the body text.
     */
    private void updateMessage(Message message, Account account, boolean hasAttachments) {
    	 Log.i("EMAIL","updateMessage enter");
        if (message.mMessageId == null || message.mMessageId.length() == 0) {
            message.mMessageId = Utility.generateMessageId();
        }
        message.mTimeStamp = System.currentTimeMillis();
        //milochen change for mFrom
        Log.i("EMAIL","account.getEmailAddress() = " + account.getEmailAddress());
        Log.i("EMAIL","account.getSenderName() = " + account.getSenderName());        
        //message.mFrom = new Address(account.getEmailAddress(), account.getSenderName()).pack();
        message.mFrom = new Address("milo.chen@mail2000.com.tw", "MiloFrom").pack();
        
        Log.i("EMAIL","message.mFrom = " + message.mFrom);
        
        
        message.mTo = getPackedAddresses(mToView);
        message.mCc = getPackedAddresses(mCcView);
        message.mBcc = getPackedAddresses(mBccView);
        message.mSubject = mSubjectView.getText().toString();
        message.mText = mMessageContentView.getText().toString();
        message.mAccountKey = account.mId;
        message.mDisplayName = makeDisplayName(message.mTo, message.mCc, message.mBcc);
        message.mFlagRead = true;
        message.mFlagLoaded = Message.FLAG_LOADED_COMPLETE;
        message.mFlagAttachment = hasAttachments;
        // Use the Intent to set flags saying this message is a reply or a forward and save the
        // unique id of the source message
        if (mSource != null && mQuotedTextBar.getVisibility() == View.VISIBLE) {
            if (ACTION_REPLY.equals(mAction) || ACTION_REPLY_ALL.equals(mAction)
                    || ACTION_FORWARD.equals(mAction)) {
                message.mSourceKey = mSource.mId;
                // Get the body of the source message here
                // Note that the following commented line will be useful when we use HTML in replies
                //message.mHtmlReply = mSource.mHtml;
                message.mTextReply = mSource.mText;
            }

            String fromAsString = Address.unpackToString(mSource.mFrom);
            Log.i("EMAIL","fromAsString = " + fromAsString);
            
            if (ACTION_FORWARD.equals(mAction)) {
                message.mFlags |= Message.FLAG_TYPE_FORWARD;
                String subject = mSource.mSubject;
                String to = Address.unpackToString(mSource.mTo);
                String cc = Address.unpackToString(mSource.mCc);
                message.mIntroText =
                    getString(R.string.message_compose_fwd_header_fmt, subject, fromAsString,
                            to != null ? to : "", cc != null ? cc : "");
            } else {
                message.mFlags |= Message.FLAG_TYPE_REPLY;
                message.mIntroText =
                    getString(R.string.message_compose_reply_header_fmt, fromAsString);
            }
        }
        Log.i("EMAIL","updateMessage leave");
    }

    private Attachment[] getAttachmentsFromUI() {
        int count = mAttachments.getChildCount();
        Attachment[] attachments = new Attachment[count];
        for (int i = 0; i < count; ++i) {
            attachments[i] = (Attachment) mAttachments.getChildAt(i).getTag();
        }
        return attachments;
    }

    /* This method does DB operations in UI thread because
       the draftId is needed by onSaveInstanceState() which can't wait for it
       to be saved in the background.
       TODO: This will cause ANRs, so we need to find a better solution.
    */
    private long getOrCreateDraftId() {
        synchronized (mDraft) {
            if (mDraft.mId > 0) {
                return mDraft.mId;
            }
            // don't save draft if the source message did not load yet 
            if (!mMessageLoaded) {
                return -1;
            }
            final Attachment[] attachments = getAttachmentsFromUI();
            updateMessage(mDraft, mAccount, attachments.length > 0);
            mController.saveToMailbox(mDraft, EmailContent.Mailbox.TYPE_DRAFTS);
            return mDraft.mId;
        }
    }

    /**
     * Send or save a message:
     * - out of the UI thread
     * - write to Drafts
     * - if send, invoke Controller.sendMessage()
     * - when operation is complete, display toast
     */
    private void sendOrSaveMessage(final boolean send) {
        final Attachment[] attachments = getAttachmentsFromUI();
        if (!mMessageLoaded) {
            // early save, before the message was loaded: do nothing
            return;
        }
        updateMessage(mDraft, mAccount, attachments.length > 0);

        mSaveMessageTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                synchronized (mDraft) {
                    if (mDraft.isSaved()) {
                        // Update the message
                        Uri draftUri =
                            ContentUris.withAppendedId(mDraft.SYNCED_CONTENT_URI, mDraft.mId);
                        getContentResolver().update(draftUri, getUpdateContentValues(mDraft),
                                null, null);
                        // Update the body
                        ContentValues values = new ContentValues();
                        values.put(BodyColumns.TEXT_CONTENT, mDraft.mText);
                        values.put(BodyColumns.TEXT_REPLY, mDraft.mTextReply);
                        values.put(BodyColumns.HTML_REPLY, mDraft.mHtmlReply);
                        values.put(BodyColumns.INTRO_TEXT, mDraft.mIntroText);
                        Body.updateBodyWithMessageId(MessageCompose.this, mDraft.mId, values);
                    } else {
                        // mDraft.mId is set upon return of saveToMailbox()
                        mController.saveToMailbox(mDraft, EmailContent.Mailbox.TYPE_DRAFTS);
                    }
                    for (Attachment attachment : attachments) {
                        if (!attachment.isSaved()) {
                            // this attachment is new so save it to DB.
                            attachment.mMessageKey = mDraft.mId;
                            attachment.save(MessageCompose.this);
                        }
                    }

                    if (send) {
                    	//milochen change since we want user to write 'FROM' field
                     //mController.sendMessage(mDraft.mId, mDraft.mAccountKey);
                    	mController.sendMessage(mDraft.mId, 12); //Gmail Account
                    	//Log.i("EMAIL","call mController.sendMessage(mDraft.mId, -2)");
                    	//mController.sendMessage(mDraft.mId, -2); //The case of -2 is defined by milo
                    	//mController.sendMessage(mDraft.mId, -1);
                        Log.i("EMAIL","mController.sendMessage(mDraft.mId, mDraft.mAccountKey)");
                        Log.i("EMAIL","mDraft.mId = " + mDraft.mId);
                        Log.i("EMAIL","mDraft.mAccountKey = " + mDraft.mAccountKey);
                    }
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Void dummy) {
                if (isCancelled()) {
                    return;
                }
                // Don't display the toast if the user is just changing the orientation
                if (!send && (getChangingConfigurations() & ActivityInfo.CONFIG_ORIENTATION) == 0) {
                    Toast.makeText(MessageCompose.this, R.string.message_saved_toast,
                            Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private void saveIfNeeded() {
        if (!mDraftNeedsSaving) {
            return;
        }
        mDraftNeedsSaving = false;
        sendOrSaveMessage(false);
    }

    /**
     * Checks whether all the email addresses listed in TO, CC, BCC are valid.
     */
    /* package */ boolean isAddressAllValid() {
        for (TextView view : new TextView[]{mToView, mCcView, mBccView}) {
            String addresses = view.getText().toString().trim();
            if (!Address.isAllValid(addresses)) {
                view.setError(getString(R.string.message_compose_error_invalid_email));
                return false;
            }
        }
        return true;
    }

    private void onSend() {
        if (!isAddressAllValid()) {
            Toast.makeText(this, getString(R.string.message_compose_error_invalid_email),
                           Toast.LENGTH_LONG).show();
        } else if (getAddresses(mToView).length == 0 &&
                getAddresses(mCcView).length == 0 &&
                getAddresses(mBccView).length == 0) {
            mToView.setError(getString(R.string.message_compose_error_no_recipients));
            Toast.makeText(this, getString(R.string.message_compose_error_no_recipients),
                    Toast.LENGTH_LONG).show();
        } else {
            sendOrSaveMessage(true);
            mDraftNeedsSaving = false;
            finish();
        }
    }

    private void onDiscard() {
        if (mDraft.mId > 0) {
            mController.deleteMessage(mDraft.mId, mDraft.mAccountKey);
        }
        Toast.makeText(this, getString(R.string.message_discarded_toast), Toast.LENGTH_LONG).show();
        mDraftNeedsSaving = false;
        finish();
    }

    private void onSave() {
        saveIfNeeded();
        finish();
    }

    private void onAddCcBcc() {
    	//milochen change
        //mCcView.setVisibility(View.VISIBLE);
        //mBccView.setVisibility(View.VISIBLE);
        mCcLayout.setVisibility(View.VISIBLE);
        mBccLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Kick off a picker for whatever kind of MIME types we'll accept and let Android take over.
     */
    private void onAddAttachment() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(Email.ACCEPTABLE_ATTACHMENT_SEND_UI_TYPES[0]);
        startActivityForResult(
                Intent.createChooser(i, getString(R.string.choose_attachment_dialog_title)),
                ACTIVITY_REQUEST_PICK_ATTACHMENT);
    }

    private Attachment loadAttachmentInfo(Uri uri) {
        int size = -1;
        String name = null;
        ContentResolver contentResolver = getContentResolver();
        Cursor metadataCursor = contentResolver.query(uri,
                ATTACHMENT_META_COLUMNS, null, null, null);
        if (metadataCursor != null) {
            try {
                if (metadataCursor.moveToFirst()) {
                    name = metadataCursor.getString(0);
                    size = metadataCursor.getInt(1);
                }
            } finally {
                metadataCursor.close();
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }

        String contentType = contentResolver.getType(uri);
        if (contentType == null) {
            contentType = "";
        }

        Attachment attachment = new Attachment();
        attachment.mFileName = name;
        attachment.mContentUri = uri.toString();
        attachment.mSize = size;
        attachment.mMimeType = contentType;
        return attachment;
    }

    private void addAttachment(Attachment attachment) {
        // Before attaching the attachment, make sure it meets any other pre-attach criteria
        if (attachment.mSize > Email.MAX_ATTACHMENT_UPLOAD_SIZE) {
            Toast.makeText(this, R.string.message_compose_attachment_size, Toast.LENGTH_LONG)
                    .show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.message_compose_attachment,
                mAttachments, false);
        TextView nameView = (TextView)view.findViewById(R.id.attachment_name);
        ImageButton delete = (ImageButton)view.findViewById(R.id.attachment_delete);
        nameView.setText(attachment.mFileName);
        delete.setOnClickListener(this);
        delete.setTag(view);
        view.setTag(attachment);
        mAttachments.addView(view);
    }

    private void addAttachment(Uri uri) {
        addAttachment(loadAttachmentInfo(uri));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	//milochen add for REQUEST_CODE_ADD_TO, REQUEST_CODE_ADD_CC and REQUEST_CODE_ADD_BCC 
    	switch (requestCode)
    	{
    	case REQUEST_CODE_ADD_TO:
    	case REQUEST_CODE_ADD_CC:
    	case REQUEST_CODE_ADD_BCC:
    		if(resultCode == RESULT_CANCELED){
    			Log.i("DEBUG", "resultCode is RESULT_CANCELED");
    			return; 
    		}
    		if(resultCode != RESULT_OK){
    			Log.i("DEBUG", "resultCode != RESULT_OK");
    			return; 
    		}
    		Log.i("DEBUG", "onActivityResult() for setting TextView data");
    		TextView textViewResult = null;
    		if(requestCode==REQUEST_CODE_ADD_TO) textViewResult = mToView;
    		if(requestCode==REQUEST_CODE_ADD_CC) textViewResult = mCcView;
    		if(requestCode==REQUEST_CODE_ADD_BCC) textViewResult = mBccView;
    		if(textViewResult != null)
			do{
				
				Bundle bunde = data.getExtras();
				String name = bunde.getString("name");
				String mail = bunde.getString("mail");
				String MailsStr = ""+bunde.getString("MailsStr");				
				//if(MailsStr.equals("") != true) MailsStr += ",\n";
				if(mail != null){
					if(name == null){
						//MailsStr +="\"" +mail+"\"<" +mail+">" ;
						MailsStr += mail+ " <" +mail+">, " ;
					}
					else
					{
						//MailsStr +="\"" +name+"\"<" +mail+">" ;
						MailsStr += name+ " <" +mail+">, " ;
					}
					
					if(textViewResult!=null){
						textViewResult.setText(MailsStr);
						Log.i("DEBUG", "onActivityResult() for textViewResult.setText("  +MailsStr+  ")");
					}
				}
			}while(false);			
    		return;
    	} //switch (requestCode)    	
    	//milochen The following code is original code.
    	//It will be run when case is not one of  REQUEST_CODE_ADD_TO, REQUEST_CODE_ADD_CC and REQUEST_CODE_ADD_BCC
    	
        if (data == null) {
            return;
        }
        addAttachment(data.getData());
        mDraftNeedsSaving = true;
    }

    public void onClick(View view) {
        switch (view.getId()) {
//            case R.id.send:
//                onSend();
//                break;
//            case R.id.save:
//                onSave();
//                break;
//            case R.id.discard:
//                onDiscard();
//                break;
            case R.id.attachment_delete:
                onDeleteAttachment(view);
                break;
            case R.id.quoted_text_delete:
                mQuotedTextBar.setVisibility(View.GONE);
                mQuotedText.setVisibility(View.GONE);
                mDraftNeedsSaving = true;
                break;
           //milochen add for case R.id.addTo, R.id.addCc and R.id.addBcc
            case R.id.addTo:
            	startEdobizContactsMailsActivity(REQUEST_CODE_ADD_TO);
            	break;
            case R.id.addCc:
            	startEdobizContactsMailsActivity(REQUEST_CODE_ADD_CC);
            	break;
            case R.id.addBcc:
            	startEdobizContactsMailsActivity(REQUEST_CODE_ADD_BCC);
            	break;
        }
        
        //milochen add
        if(true == isInOptionsButton(view.getId()) ){
     	   onOptionsButtonSelected((Button)view);
         } 
    } //onClick()
    
	public void startEdobizContactsMailsActivity(int requestCode){
		Log.i("DEBUG", "startEdobizContactsMailsActivity() enter");
		switch(requestCode)
		{
		case REQUEST_CODE_ADD_TO:
		case REQUEST_CODE_ADD_CC:
		case REQUEST_CODE_ADD_BCC:
	    	String MailsStr="";
	    	if(requestCode==REQUEST_CODE_ADD_TO) MailsStr+=mToView.getText();
	    	if(requestCode==REQUEST_CODE_ADD_CC) MailsStr+=mCcView.getText();
	    	if(requestCode==REQUEST_CODE_ADD_BCC) MailsStr+=mBccView.getText();
	    	Intent i = new Intent("com.android.providers.contacts.edobizContactsMailsActivity");
	    	i.putExtra("MailsStr", MailsStr);
	    	i.setComponent(new ComponentName("com.android.providers.contacts","com.android.providers.contacts.edobizContactsMailsActivity"));      	
	    	this.startActivityForResult(i , requestCode);
	    	break;
		}
		Log.i("DEBUG", "startEdobizContactsMailsActivity() leave");
	}
    
    

	
    private void onDeleteAttachment(View delButtonView) {
        /*
         * The view is the delete button, and we have previously set the tag of
         * the delete button to the view that owns it. We don't use parent because the
         * view is very complex and could change in the future.
         */
        View attachmentView = (View) delButtonView.getTag();
        Attachment attachment = (Attachment) attachmentView.getTag();
        mAttachments.removeView(attachmentView);
        if (attachment.isSaved()) {
            // The following async task for deleting attachments:
            // - can be started multiple times in parallel (to delete multiple attachments).
            // - need not be interrupted on activity exit, instead should run to completion.
            new AsyncTask<Long, Void, Void>() {
                @Override
                protected Void doInBackground(Long... attachmentIds) {
                    mController.deleteAttachment(attachmentIds[0]);
                    return null;
                }
            }.execute(attachment.mId);
        }
        mDraftNeedsSaving = true;
    }

    
    //milochen add [ some button is not original in onOptionsItemSelected(), so the extension function well show here ]
    public boolean onOptionsButtonExtensionSelected(Button btn) {
    	switch (btn.getId()){
        case R.id.backkey:
            finish();
            return true;
        default:
    		return false;
    	}
    }
    
    //milochen add [ by reference onOptionsItemSelected() ]
    public boolean onOptionsButtonSelected(Button btn) {
    	switch (btn.getId()){
        case R.id.send:
            onSend();
            return true;
        case R.id.save:
            onSave();
            return true;
        case R.id.discard:
            onDiscard();
            return true;
        case R.id.add_cc_bcc:
            onAddCcBcc();
            return true;
        case R.id.add_attachment:
            onAddAttachment();
            return true;
        default:
        	return onOptionsButtonExtensionSelected(btn);
    		
    	}
    }
    
    //milochen add
    public boolean isInOptionsButton(int id){
    	int idx;
    	for (idx = 0; idx < mOptionsButtonIds.length; idx++){
    		if(id == mOptionsButtonIds[idx]) return true;
    	}
    	return false;
    }    
    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send:
                onSend();
                break;
            case R.id.save:
                onSave();
                break;
            case R.id.discard:
                onDiscard();
                break;
            case R.id.add_cc_bcc:
                onAddCcBcc();
                break;
            case R.id.add_attachment:
                onAddAttachment();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.message_compose_option, menu);
        return true;
    }

    /**
     * Returns true if all attachments were able to be attached, otherwise returns false.
     */
//     private boolean loadAttachments(Part part, int depth) throws MessagingException {
//         if (part.getBody() instanceof Multipart) {
//             Multipart mp = (Multipart) part.getBody();
//             boolean ret = true;
//             for (int i = 0, count = mp.getCount(); i < count; i++) {
//                 if (!loadAttachments(mp.getBodyPart(i), depth + 1)) {
//                     ret = false;
//                 }
//             }
//             return ret;
//         } else {
//             String contentType = MimeUtility.unfoldAndDecode(part.getContentType());
//             String name = MimeUtility.getHeaderParameter(contentType, "name");
//             if (name != null) {
//                 Body body = part.getBody();
//                 if (body != null && body instanceof LocalAttachmentBody) {
//                     final Uri uri = ((LocalAttachmentBody) body).getContentUri();
//                     mHandler.post(new Runnable() {
//                         public void run() {
//                             addAttachment(uri);
//                         }
//                     });
//                 }
//                 else {
//                     return false;
//                 }
//             }
//             return true;
//         }
//     }

    /**
     * Fill all the widgets with the content found in the Intent Extra, if any.
     *
     * Note that we don't actually check the intent action  (typically VIEW, SENDTO, or SEND).
     * There is enough overlap in the definitions that it makes more sense to simply check for
     * all available data and use as much of it as possible.
     *
     * With one exception:  EXTRA_STREAM is defined as only valid for ACTION_SEND.
     *
     * @param intent the launch intent
     */
    /* package */ void initFromIntent(Intent intent) {

        // First, add values stored in top-level extras

        String[] extraStrings = intent.getStringArrayExtra(Intent.EXTRA_EMAIL);
        if (extraStrings != null) {
            addAddresses(mToView, extraStrings);
        }
        extraStrings = intent.getStringArrayExtra(Intent.EXTRA_CC);
        if (extraStrings != null) {
            addAddresses(mCcView, extraStrings);
        }
        extraStrings = intent.getStringArrayExtra(Intent.EXTRA_BCC);
        if (extraStrings != null) {
            addAddresses(mBccView, extraStrings);
        }
        String extraString = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (extraString != null) {
            mSubjectView.setText(extraString);
        }

        // Next, if we were invoked with a URI, try to interpret it
        // We'll take two courses here.  If it's mailto:, there is a specific set of rules
        // that define various optional fields.  However, for any other scheme, we'll simply
        // take the entire scheme-specific part and interpret it as a possible list of addresses.

        final Uri dataUri = intent.getData();
        if (dataUri != null) {
            if ("mailto".equals(dataUri.getScheme())) {
                initializeFromMailTo(dataUri.toString());
            } else {
                String toText = dataUri.getSchemeSpecificPart();
                if (toText != null) {
                    addAddresses(mToView, toText.split(","));
                }
            }
        }

        // Next, fill in the plaintext (note, this will override mailto:?body=)

        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            mMessageContentView.setText(text);
        }

        // Next, convert EXTRA_STREAM into an attachment

        if (Intent.ACTION_SEND.equals(mAction) && intent.hasExtra(Intent.EXTRA_STREAM)) {
            String type = intent.getType();
            Uri stream = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null && type != null) {
                if (MimeUtility.mimeTypeMatches(type,
                        Email.ACCEPTABLE_ATTACHMENT_SEND_INTENT_TYPES)) {
                    addAttachment(stream);
                }
            }
        }

        if (Intent.ACTION_SEND_MULTIPLE.equals(mAction)
                && intent.hasExtra(Intent.EXTRA_STREAM)) {
            ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) {
                for (Parcelable parcelable : list) {
                    Uri uri = (Uri) parcelable;
                    if (uri != null) {
                        Attachment attachment = loadAttachmentInfo(uri);
                        if (MimeUtility.mimeTypeMatches(attachment.mMimeType, 
                                Email.ACCEPTABLE_ATTACHMENT_SEND_INTENT_TYPES)) {
                            addAttachment(attachment);
                        }
                    }
                }
            }
        }

        // Finally - expose fields that were filled in but are normally hidden, and set focus

        if (mCcView.length() > 0) {
            mCcView.setVisibility(View.VISIBLE);
        }
        if (mBccView.length() > 0) {
            mBccView.setVisibility(View.VISIBLE);
        }
        setNewMessageFocus();
        mDraftNeedsSaving = false;
    }

    /**
     * When we are launched with an intent that includes a mailto: URI, we can actually
     * gather quite a few of our message fields from it.
     *
     * @mailToString the href (which must start with "mailto:").
     */
    private void initializeFromMailTo(String mailToString) {

        // Chop up everything between mailto: and ? to find recipients
        int index = mailToString.indexOf("?");
        int length = "mailto".length() + 1;
        String to;
        try {
            // Extract the recipient after mailto:
            if (index == -1) {
                to = decode(mailToString.substring(length));
            } else {
                to = decode(mailToString.substring(length, index));
            }
            addAddresses(mToView, to.split(" ,"));
        } catch (UnsupportedEncodingException e) {
            Log.e(Email.LOG_TAG, e.getMessage() + " while decoding '" + mailToString + "'");
        }

        // Extract the other parameters

        // We need to disguise this string as a URI in order to parse it
        Uri uri = Uri.parse("foo://" + mailToString);

        List<String> cc = uri.getQueryParameters("cc");
        addAddresses(mCcView, cc.toArray(new String[cc.size()]));

        List<String> otherTo = uri.getQueryParameters("to");
        addAddresses(mCcView, otherTo.toArray(new String[otherTo.size()]));

        List<String> bcc = uri.getQueryParameters("bcc");
        addAddresses(mBccView, bcc.toArray(new String[bcc.size()]));

        List<String> subject = uri.getQueryParameters("subject");
        if (subject.size() > 0) {
            mSubjectView.setText(subject.get(0));
        }

        List<String> body = uri.getQueryParameters("body");
        if (body.size() > 0) {
            mMessageContentView.setText(body.get(0));
        }
    }

    private String decode(String s) throws UnsupportedEncodingException {
        return URLDecoder.decode(s, "UTF-8");
    }

    // used by processSourceMessage()
    private void displayQuotedText(String textBody, String htmlBody) {
        /* Use plain-text body if available, otherwise use HTML body.
         * This matches the desired behavior for IMAP/POP where we only send plain-text,
         * and for EAS which sends HTML and has no plain-text body.
         */
        boolean plainTextFlag = textBody != null;
        String text = plainTextFlag ? textBody : htmlBody;
        if (text != null) {
            text = plainTextFlag ? EmailHtmlUtil.escapeCharacterToDisplay(text) : text;
            // TODO: re-enable EmailHtmlUtil.resolveInlineImage() for HTML
            //    EmailHtmlUtil.resolveInlineImage(getContentResolver(), mAccount,
            //                                     text, message, 0);
            mQuotedTextBar.setVisibility(View.VISIBLE);
            mQuotedText.setVisibility(View.VISIBLE);
            mQuotedText.loadDataWithBaseURL("email://", text, "text/html",
                                            "utf-8", null);
        }
    }

    /**
     * Given a packed address String, the address of our sending account, a view, and a list of
     * addressees already added to other addressing views, adds unique addressees that don't
     * match our address to the passed in view
     */
    private boolean safeAddAddresses(String addrs, String ourAddress,
            MultiAutoCompleteTextView view, ArrayList<Address> addrList) {
        boolean added = false;
        for (Address address : Address.unpack(addrs)) {
            // Don't send to ourselves or already-included addresses
            if (!address.getAddress().equalsIgnoreCase(ourAddress) && !addrList.contains(address)) {
                addrList.add(address);
                addAddress(view, address.toString());
                added = true;
            }
        }
        return added;
    }

    /**
     * Set up the to and cc views properly for the "reply" and "replyAll" cases.  What's important
     * is that we not 1) send to ourselves, and 2) duplicate addressees.
     * @param message the message we're replying to
     * @param account the account we're sending from
     * @param toView the "To" view
     * @param ccView the "Cc" view
     * @param replyAll whether this is a replyAll (vs a reply)
     */
    /*package*/ void setupAddressViews(Message message, Account account,
            MultiAutoCompleteTextView toView, MultiAutoCompleteTextView ccView, boolean replyAll) {
        /*
         * If a reply-to was included with the message use that, otherwise use the from
         * or sender address.
         */
        Address[] replyToAddresses = Address.unpack(message.mReplyTo);
        if (replyToAddresses.length == 0) {
            replyToAddresses = Address.unpack(message.mFrom);
        }
        addAddresses(mToView, replyToAddresses);

        if (replyAll) {
            // Keep a running list of addresses we're sending to
            ArrayList<Address> allAddresses = new ArrayList<Address>();
            String ourAddress = account.mEmailAddress;

            for (Address address: replyToAddresses) {
                allAddresses.add(address);
            }

            safeAddAddresses(message.mTo, ourAddress, mToView, allAddresses);
            if (safeAddAddresses(message.mCc, ourAddress, mCcView, allAddresses)) {
                mCcView.setVisibility(View.VISIBLE);
            }
        }
    }

    void processSourceMessageGuarded(Message message, Account account) {
        // Make sure we only do this once (otherwise we'll duplicate addresses!)
        if (!mSourceMessageProcessed) {
            processSourceMessage(message, account);
            mSourceMessageProcessed = true;
        }

        /* The quoted text is displayed in a WebView whose content is not automatically
         * saved/restored by onRestoreInstanceState(), so we need to *always* restore it here,
         * regardless of the value of mSourceMessageProcessed.
         * This only concerns EDIT_DRAFT because after a configuration change we're always
         * in EDIT_DRAFT.
         */
        if (ACTION_EDIT_DRAFT.equals(mAction)) {
            displayQuotedText(message.mTextReply, message.mHtmlReply);
        }
    }

    /**
     * Pull out the parts of the now loaded source message and apply them to the new message
     * depending on the type of message being composed.
     * @param message
     */
    /* package */
    void processSourceMessage(Message message, Account account) {
        mDraftNeedsSaving = true;
        final String subject = message.mSubject;
        if (ACTION_REPLY.equals(mAction) || ACTION_REPLY_ALL.equals(mAction)) {
            setupAddressViews(message, account, mToView, mCcView,
                ACTION_REPLY_ALL.equals(mAction));
            if (subject != null && !subject.toLowerCase().startsWith("re:")) {
                mSubjectView.setText("Re: " + subject);
            } else {
                mSubjectView.setText(subject);
            }
            displayQuotedText(message.mText, message.mHtml);
        } else if (ACTION_FORWARD.equals(mAction)) {
            mSubjectView.setText(subject != null && !subject.toLowerCase().startsWith("fwd:") ?
                    "Fwd: " + subject : subject);
            displayQuotedText(message.mText, message.mHtml);
                // TODO: re-enable loadAttachments below
//                 if (!loadAttachments(message, 0)) {
//                     mHandler.sendEmptyMessage(MSG_SKIPPED_ATTACHMENTS);
//                 }
        } else if (ACTION_EDIT_DRAFT.equals(mAction)) {
            mSubjectView.setText(subject);
            addAddresses(mToView, Address.unpack(message.mTo));
            Address[] cc = Address.unpack(message.mCc);
            if (cc.length > 0) {
                addAddresses(mCcView, cc);
                mCcView.setVisibility(View.VISIBLE);
            }
            Address[] bcc = Address.unpack(message.mBcc);
            if (bcc.length > 0) {
                addAddresses(mBccView, bcc);
                mBccView.setVisibility(View.VISIBLE);
            }

            mMessageContentView.setText(message.mText);
            // TODO: re-enable loadAttachments
            // loadAttachments(message, 0);
            mDraftNeedsSaving = false;
        }
        setNewMessageFocus();
    }

    /**
     * In order to accelerate typing, position the cursor in the first empty field,
     * or at the end of the body composition field if none are empty.  Typically, this will
     * play out as follows:
     *   Reply / Reply All - put cursor in the empty message body
     *   Forward - put cursor in the empty To field
     *   Edit Draft - put cursor in wandroid:id="@+id/bcc_layout"hatever field still needs entry
     */
    private void setNewMessageFocus() {
        if (mToView.length() == 0) {
            mToView.requestFocus();
        } else if (mSubjectView.length() == 0) {
            mSubjectView.requestFocus();
        } else {
            mMessageContentView.requestFocus();
            // when selecting the message content, explicitly move IP to the end, so you can
            // quickly resume typing into a draft
            int selection = mMessageContentView.length();
            mMessageContentView.setSelection(selection, selection);
        }
    }

    class Listener implements Controller.Result {
        public void updateMailboxListCallback(MessagingException result, long accountId,
                int progress) {
        }

        public void updateMailboxCallback(MessagingException result, long accountId,
                long mailboxId, int progress, int numNewMessages) {
            if (result != null || progress == 100) {
                Email.updateMailboxRefreshTime(mailboxId);
            }
        }

        public void loadMessageForViewCallback(MessagingException result, long messageId,
                int progress) {
        }

        public void loadAttachmentCallback(MessagingException result, long messageId,
                long attachmentId, int progress) {
        }

        public void serviceCheckMailCallback(MessagingException result, long accountId,
                long mailboxId, int progress, long tag) {
        }

        public void sendMailCallback(MessagingException result, long accountId, long messageId,
                int progress) {
        }
    }

//     class Listener extends MessagingListener {
//         @Override
//         public void loadMessageForViewStarted(Account account, String folder,
//                 String uid) {
//             mHandler.sendEmptyMessage(MSG_PROGRESS_ON);
//         }

//         @Override
//         public void loadMessageForViewFinished(Account account, String folder,
//                 String uid, Message message) {
//             mHandler.sendEmptyMessage(MSG_PROGRESS_OFF);
//         }

//         @Override
//         public void loadMessageForViewBodyAvailable(Account account, String folder,
//                 String uid, final Message message) {
//            // TODO: convert uid to EmailContent.Message and re-do what's below
//             mSourceMessage = message;
//             runOnUiThread(new Runnable() {
//                 public void run() {
//                     processSourceMessage(message);
//                 }
//             });
//         }

//         @Override
//         public void loadMessageForViewFailed(Account account, String folder, String uid,
//                 final String message) {
//             mHandler.sendEmptyMessage(MSG_PROGRESS_OFF);
//             // TODO show network error
//         }
//     }
}