/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui.dialog;

import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.Id;
import org.sufficientlysecure.keychain.helper.PgpHelper;
import org.sufficientlysecure.keychain.helper.PgpMain;
import org.sufficientlysecure.keychain.helper.PgpMain.PgpGeneralException;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;


import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class PassphraseDialogFragment extends DialogFragment implements OnEditorActionListener {
    private static final String ARG_MESSENGER = "messenger";
    private static final String ARG_SECRET_KEY_ID = "secret_key_id";

    public static final int MESSAGE_OKAY = 1;

    private Messenger mMessenger;
    private EditText mPassphraseEditText;

    /**
     * Creates new instance of this dialog fragment
     * 
     * @param secretKeyId
     *            secret key id you want to use
     * @param messenger
     *            to communicate back after caching the passphrase
     * @return
     * @throws PgpGeneralException
     */
    public static PassphraseDialogFragment newInstance(Context context, Messenger messenger,
            long secretKeyId) throws PgpGeneralException {
        // check if secret key has a passphrase
        if (!(secretKeyId == Id.key.symmetric || secretKeyId == Id.key.none)) {
            if (!hasPassphrase(context, secretKeyId)) {
                throw new PgpMain.PgpGeneralException("No passphrase! No passphrase dialog needed!");
            }
        }

        PassphraseDialogFragment frag = new PassphraseDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SECRET_KEY_ID, secretKeyId);
        args.putParcelable(ARG_MESSENGER, messenger);

        frag.setArguments(args);

        return frag;
    }

    /**
     * Checks if key has a passphrase
     * 
     * @param secretKeyId
     * @return true if it has a passphrase
     */
    private static boolean hasPassphrase(Context context, long secretKeyId) {
        // check if the key has no passphrase
        try {
            PGPSecretKey secretKey = PgpHelper.getMasterKey(ProviderHelper
                    .getPGPSecretKeyRingByKeyId(context, secretKeyId));
            // PGPSecretKey secretKey =
            // PGPHelper.getMasterKey(PGPMain.getSecretKeyRing(secretKeyId));

            Log.d(Constants.TAG, "Check if key has no passphrase...");
            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                    "SC").build("".toCharArray());
            PGPPrivateKey testKey = secretKey.extractPrivateKey(keyDecryptor);
            if (testKey != null) {
                Log.d(Constants.TAG, "Key has no passphrase! Caches empty passphrase!");

                // cache empty passphrase
                PassphraseCacheService.addCachedPassphrase(context, secretKey.getKeyID(), "");

                return false;
            }
        } catch (PGPException e) {
            // silently catch
        }

        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Creates dialog
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();

        long secretKeyId = getArguments().getLong(ARG_SECRET_KEY_ID);
        mMessenger = getArguments().getParcelable(ARG_MESSENGER);

        AlertDialog.Builder alert = new AlertDialog.Builder(activity);

        alert.setTitle(R.string.title_authentication);

        final PGPSecretKey secretKey;

        if (secretKeyId == Id.key.symmetric || secretKeyId == Id.key.none) {
            secretKey = null;
            alert.setMessage(R.string.passPhraseForSymmetricEncryption);
        } else {
            // TODO: by master key id???
            secretKey = PgpHelper.getMasterKey(ProviderHelper.getPGPSecretKeyRingByMasterKeyId(
                    activity, secretKeyId));
            // secretKey = PGPHelper.getMasterKey(PGPMain.getSecretKeyRing(secretKeyId));

            if (secretKey == null) {
                alert.setTitle(R.string.title_keyNotFound);
                alert.setMessage(getString(R.string.keyNotFound, secretKeyId));
                alert.setPositiveButton(android.R.string.ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                });
                alert.setCancelable(false);
                return alert.create();
            }
            String userId = PgpHelper.getMainUserIdSafe(activity, secretKey);

            Log.d(Constants.TAG, "User id: '" + userId + "'");
            alert.setMessage(getString(R.string.passPhraseFor, userId));
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.passphrase, null);
        alert.setView(view);

        mPassphraseEditText = (EditText) view.findViewById(R.id.passphrase_passphrase);

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dismiss();

                String passPhrase = mPassphraseEditText.getText().toString();
                long keyId;
                if (secretKey != null) {
                    try {
                        PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder()
                                .setProvider(PgpMain.BOUNCY_CASTLE_PROVIDER_NAME).build(
                                        passPhrase.toCharArray());
                        PGPPrivateKey testKey = secretKey.extractPrivateKey(keyDecryptor);
                        if (testKey == null) {
                            Toast.makeText(activity, R.string.error_couldNotExtractPrivateKey,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (PGPException e) {
                        Toast.makeText(activity, R.string.wrongPassPhrase, Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    keyId = secretKey.getKeyID();
                } else {
                    keyId = Id.key.symmetric;
                }

                // cache the new passphrase
                Log.d(Constants.TAG, "Everything okay! Caching entered passphrase");
                PassphraseCacheService.addCachedPassphrase(activity, keyId, passPhrase);

                sendMessageToHandler(MESSAGE_OKAY);
            }
        });

        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dismiss();
            }
        });

        return alert.create();
    }

    @Override
    public void onActivityCreated(Bundle arg0) {
        super.onActivityCreated(arg0);

        // request focus and open soft keyboard
        mPassphraseEditText.requestFocus();
        getDialog().getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        mPassphraseEditText.setOnEditorActionListener(this);
    }

    /**
     * Associate the "done" button on the soft keyboard with the okay button in the view
     */
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (EditorInfo.IME_ACTION_DONE == actionId) {
            AlertDialog dialog = ((AlertDialog) getDialog());
            Button bt = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            bt.performClick();
            return true;
        }
        return false;
    }

    /**
     * Send message back to handler which is initialized in a activity
     * 
     * @param what
     *            Message integer you want to send
     */
    private void sendMessageToHandler(Integer what) {
        Message msg = Message.obtain();
        msg.what = what;

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(Constants.TAG, "Exception sending message, Is handler present?", e);
        } catch (NullPointerException e) {
            Log.w(Constants.TAG, "Messenger is null!", e);
        }
    }

}