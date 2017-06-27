package com.arena.software.xand0;

import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import android.widget.ImageView;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.content.DialogInterface;

import android.util.Log;

import com.arena.software.xand0.util.IabBroadcastReceiver;
import com.arena.software.xand0.util.IabBroadcastReceiver.IabBroadcastListener;
import com.arena.software.xand0.util.IabHelper;
import com.arena.software.xand0.util.IabHelper.IabAsyncInProgressException;
import com.arena.software.xand0.util.IabResult;
import com.arena.software.xand0.util.Inventory;
import com.arena.software.xand0.util.Purchase;

public class InAppPurchaseActivity extends AppCompatActivity implements IabBroadcastListener {
    public static final int BILLING_RESPONSE_RESULT_OK = 0;

    // The helper object
    IabHelper mHelper;
    // Provides purchase notification while this app is running
    IabBroadcastReceiver mBroadcastReceiver;
    static final String TAG = "Xand0";

    static final String SKU_small = "small_support";
    static final String SKU_medium = "medium_support";
    static final String SKU_heavy = "heavy_support";


    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_app_purchase);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
         * (that you got from the Google Play developer console). This is not your
         * developer public key, it's the *app-specific* public key.
         *
         * Instead of just storing the entire literal string here embedded in the
         * program,  construct the key at runtime from pieces or
         * use bit manipulation (for example, XOR with some other string) to hide
         * the actual key.  The key itself is not secret information, but we don't
         * want to make it easy for an attacker to replace the public key with one
         * of their own and then fake messages from the server.
         */
        String base64EncodedPublicKey = getBaseKey();

        if (base64EncodedPublicKey.contains("CONSTRUCT_YOUR")) {
            throw new RuntimeException("Please put your app's public key in MainActivity.java. See README.");
        }
        if (getPackageName().startsWith("com.example")) {
            throw new RuntimeException("Please change the sample's package name! See README.");
        }

        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(this, base64EncodedPublicKey);

        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Log.d(TAG, "Setup finished.");

                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    complain("Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                // Important: Dynamically register for broadcast messages about updated purchases.
                // We register the receiver here instead of as a <receiver> in the Manifest
                // because we always call getPurchases() at startup, so therefore we can ignore
                // any broadcasts sent while the app isn't running.
                // Note: registering this listener in an Activity is a bad idea, but is done here
                // because this is a SAMPLE. Regardless, the receiver must be registered after
                // IabHelper is setup, but before first call to getPurchases().
                mBroadcastReceiver = new IabBroadcastReceiver(InAppPurchaseActivity.this);
                IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
                registerReceiver(mBroadcastReceiver, broadcastFilter);

                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                try {
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error querying inventory. Another async operation in progress.");
                }
            }
        });
    }


    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                complain("Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            // Do we have the premium upgrade?
            Purchase smallSupport = inventory.getPurchase(SKU_small);
            Purchase mediumSupport = inventory.getPurchase(SKU_medium);
            Purchase heavySupport = inventory.getPurchase(SKU_heavy);

            boolean bSmallSupport = (smallSupport != null && verifyDeveloperPayload(smallSupport));
            boolean bMediumSupport = (mediumSupport != null && verifyDeveloperPayload(mediumSupport));
            boolean bHeavySupport = (heavySupport != null && verifyDeveloperPayload(heavySupport));

            if (bSmallSupport) {
                Log.d(TAG, "Small support. consume it");
                try {
                    mHelper.consumeAsync(inventory.getPurchase(SKU_small), mConsumeFinishedListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error consuming small support. Another async operation in progress.");
                }
                return;
            } else if (bMediumSupport) {
                Log.d(TAG, "Medium support. consume it");
                try {
                    mHelper.consumeAsync(inventory.getPurchase(SKU_medium), mConsumeFinishedListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error consuming medium support. Another async operation in progress.");
                }
                return;
            } else if (bHeavySupport) {
                Log.d(TAG, "Heavy support. consume it");
                try {
                    mHelper.consumeAsync(inventory.getPurchase(SKU_heavy), mConsumeFinishedListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error consuming heavy support. Another async operation in progress.");
                }
                return;
            }

            Log.d(TAG, "Initial inventory query finished;");
        }
    };


    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                complain("Error purchasing: " + result);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_small)) {
                Log.d(TAG, "Purchase is small support. Consume");
                try {
                    mHelper.consumeAsync(purchase, mConsumeFinishedListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error consuming small Support. Another async operation in progress.");
                    return;
                }
            } else if (purchase.getSku().equals(SKU_medium)) {
                Log.d(TAG, "Purchase is medium support. Consume");
                try {
                    mHelper.consumeAsync(purchase, mConsumeFinishedListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error consuming medium Support. Another async operation in progress.");
                    return;
                }
            } else if (purchase.getSku().equals(SKU_heavy)) {
                Log.d(TAG, "Purchase is heavy support. Consume");
                try {
                    mHelper.consumeAsync(purchase, mConsumeFinishedListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error consuming heavy Support. Another async operation in progress.");
                    return;
                }
            }
        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            int width = (int) getResources().getDimension(R.dimen.table_size);
            int height = (int) getResources().getDimension(R.dimen.table_size);


            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (purchase.getSku().equals(SKU_small)) {

                if (result.isSuccess()) {
                    // successfully consumed, so we apply the effects of the item in our
                    Log.d(TAG, "Small support consumption successful.");

                    ImageView img = (ImageView) findViewById(R.id.SmallSupportPicture);
                    loadPhoto(img, width, height);

                } else {
                    complain("Error while consuming: " + result);
                }
                Log.d(TAG, "End consumption flow.");
            } else if (purchase.getSku().equals(SKU_medium)) {

                if (result.isSuccess()) {
                    Log.d(TAG, "Medium support consumption successful.");
                    ImageView img = (ImageView) findViewById(R.id.MediumSupportPicture);
                    loadPhoto(img, width, height);
                } else {
                    complain("Error while consuming: " + result);
                }
                Log.d(TAG, "End consumption flow.");
            } else if (purchase.getSku().equals(SKU_heavy)) {

                if (result.isSuccess()) {
                    Log.d(TAG, "Heavy support consumption successful.");
                    ImageView img = (ImageView) findViewById(R.id.HeavySupportPicture);
                    loadPhoto(img, width, height);
                } else {
                    complain("Error while consuming: " + result);
                }
                Log.d(TAG, "End consumption flow.");
            }
        }

    };


    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            mHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabAsyncInProgressException e) {
            complain("Error querying inventory. Another async operation in progress.");
        }
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    public void onDonate(View v) {
         /* TODO: for security, generate your payload here for verification. See the comments on
             *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
             *        an empty string, but on a production app you should carefully generate
             *        this. */
        String payload = "";

        Log.d(TAG, "Launching donate workflow");
        switch (v.getId()) {
            case R.id.supportSmall:
                try {
                    //http://stackoverflow.com/questions/15575605/android-in-app-billing-cant-start-async-operation-because-another-async-operat
                    //Async is not closed automatically, need to be done manually
                    if(mHelper != null)
                        mHelper.flagEndAsync();
                    mHelper.launchPurchaseFlow(this, SKU_small, RC_REQUEST,
                            mPurchaseFinishedListener, payload);
                } catch (IabAsyncInProgressException e) {
                    complain("Error launching purchase flow. Another async operation in progress.");
                }
                break;
            case R.id.supportMedium:
                try {
                    if(mHelper != null)
                        mHelper.flagEndAsync();
                    mHelper.launchPurchaseFlow(this, SKU_medium, RC_REQUEST,
                            mPurchaseFinishedListener, payload);
                } catch (IabAsyncInProgressException e) {
                    complain("Error launching purchase flow. Another async operation in progress.");
                }
                break;
            case R.id.supportHeavy:
                try {
                    if(mHelper != null)
                        mHelper.flagEndAsync();
                    mHelper.launchPurchaseFlow(this, SKU_heavy, RC_REQUEST,
                            mPurchaseFinishedListener, payload);
                } catch (IabAsyncInProgressException e) {
                    complain("Error launching purchase flow. Another async operation in progress.");
                }
                break;
            default:
                break;
        }

    }

    void complain(String message) {
        Log.e(TAG, "**** X and O Error: " + message);
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
    }

    private void loadPhoto(ImageView imageView, int width, int height) {

        //http://stackoverflow.com/questions/6044793/popupwindow-with-image

        ImageView tempImageView = imageView;


        AlertDialog.Builder imageDialog = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

        View layout = inflater.inflate(R.layout.custom_fullimage_dialog,
                (ViewGroup) findViewById(R.id.layout_root));
        ImageView image = (ImageView) layout.findViewById(R.id.fullimage);
        image.setImageDrawable(tempImageView.getDrawable());
        imageDialog.setView(layout);
        imageDialog.setPositiveButton(getResources().getString(R.string.ok_button), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });


        imageDialog.create();
        imageDialog.show();
    }
    private String getBaseKey()
    {
        String baseKey = getBasekeyPart1() + getBasekeyPart2() + getBasekeyPart3() + getBasekeyPart4();
        return baseKey;
    }
    //
    //
    private String getBasekeyPart1()
    {
        String str = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmsR+r2bCj4sQ/xJ/";
        return str;
    }
    private String getBasekeyPart2()
    {
        String str = "6jhxENyYCSXDfz7kP7xeDnlpzEM8yTh/4yPZ4S5EFGMWfsJMzGE0/LhZixgifeZ4prdHsMMQXWmj3INfU2FBBAs1biHMkk/";
        return str;
    }
    private String getBasekeyPart3()
    {
        String str = "Sfdzr+sCy2oa7l6PZqUn1zKjAaNpFZCNt43rH0VrBnMmJY8o9ePNg4HdlBbnFv2Bzvk+xDDqzDeCURGe1O3RT4kQacivGqk21UGlhgrMRBMtrsc7F5YyveQKf39SHtEbRHo+26YlpO8+";
        return str;
    }
    private String getBasekeyPart4()
    {
        String str = "8kRR2VMFCHU0P1MGEzbilI+ElIUyHpo8fvlvLBOl2C9ytsBFuAi/a+4FHyh3b+/6Md+MbF8h6891NICPwJRv1ZvLJmQIDAQAB";
        return str;
    }

}
