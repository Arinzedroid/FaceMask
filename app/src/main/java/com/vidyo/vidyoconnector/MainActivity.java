package com.vidyo.vidyoconnector;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Endpoint.LogRecord;
import com.vidyo.VidyoClient.NetworkInterface;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends Activity implements
        Connector.IConnect,
        Connector.IRegisterLogEventListener,
        Connector.IRegisterNetworkInterfaceEventListener,
        IVideoFrameListener {

    enum VIDYO_CONNECTOR_STATE {
        VC_CONNECTED,
        VC_DISCONNECTED,
        VC_DISCONNECTED_UNEXPECTED,
        VC_CONNECTION_FAILURE
    }

    private String TAG = MainActivity.class.getSimpleName();

    private VIDYO_CONNECTOR_STATE mVidyoConnectorState = VIDYO_CONNECTOR_STATE.VC_DISCONNECTED;
    private boolean mVidyoClientInitialized = false;
    private Logger mLogger = Logger.getInstance();
    private Connector mVidyoConnector = null;
    private ToggleButton mToggleConnectButton;
    private ProgressBar mConnectionSpinner;
    private LinearLayout mControlsLayout;
    private LinearLayout mToolbarLayout;
    private EditText mDisplayName;
    private EditText mResourceId;
    private TextView mToolbarStatus;
    private TextView mClientVersion;
    private VideoFrameLayout mVideoFrame;
    private boolean mHideConfig = false;
    private boolean mAutoJoin = false;
    private boolean mAllowReconnect = true;
    private boolean mCameraPrivacy = false;
    private boolean mMicrophonePrivacy = false;
    private boolean mEnableDebug = false;
    private String mReturnURL = null;
    private String mExperimentalOptions = null;
    private MainActivity mSelf;
    SharedPreferences prefs;
    String Display_Name,ResourceID;
    int m_permissionCode = 100;


    /*
     *  Operating System Events
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLogger.Log("onCreate");
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 23) {
            init();
        }
        else {
            checkPermissions(m_permissionCode);
        }

    }

    public void init(){
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = this.getSharedPreferences("com.facemask.app", Context.MODE_PRIVATE);

        // Initialize the member variables
        mToggleConnectButton = (ToggleButton) findViewById(R.id.toggleConnectButton);
        mControlsLayout = (LinearLayout) findViewById(R.id.controlsLayout);
        mToolbarLayout = (LinearLayout) findViewById(R.id.toolbarLayout);
        mVideoFrame = (VideoFrameLayout) findViewById(R.id.videoFrame);
        mVideoFrame.Register(this);
        mDisplayName = (EditText) findViewById(R.id.displayNameTextBox);
        mResourceId = (EditText) findViewById(R.id.resourceIdTextBox);
        mToolbarStatus = (TextView) findViewById(R.id.toolbarStatusText);
        mClientVersion = (TextView) findViewById(R.id.clientVersion);
        mConnectionSpinner = (ProgressBar) findViewById(R.id.connectionSpinner);
        mSelf = this;

        Display_Name = prefs.getString("DisplayName","");
        ResourceID = prefs.getString("ResourceID","");

        // Suppress keyboard
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // Initialize the VidyoClient
        ConnectorPkg.setApplicationUIContext(this);
        mVidyoClientInitialized = ConnectorPkg.initialize();
    }

    public void checkPermissions(int Code){
        // require permission to access camera, read and write external storage
        String[] permissions_required = new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CAPTURE_AUDIO_OUTPUT,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE };

        // check if permissions have been granted
        List<String> permissions_not_granted_list = new ArrayList<>();
        for (String permission : permissions_required) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                permissions_not_granted_list.add(permission);
            }
        }
        // permissions not granted
        if (permissions_not_granted_list.size() > 0) {
            String[] permissions = new String[permissions_not_granted_list.size()];
            permissions_not_granted_list.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, Code);
        }
        else { // if all permissions have been granted
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],@NonNull int[] grantResults) {
        // this is the answer to our permission request (our permissioncode)
        if (requestCode == m_permissionCode) {
            // check if all have been granted
            boolean ok = true;
            for (int grantResult : grantResults) {
                ok = ok && (grantResult == PackageManager.PERMISSION_GRANTED);
            }
            if (ok) {
                // if all have been granted, continue
                init();
            }
            else {
                // exit if not all required permissions have been granted
                Toast.makeText(this, "Error: required permissions not granted!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }




    @Override
    protected void onNewIntent(Intent intent) {
        mLogger.Log("onNewIntent");
        super.onNewIntent(intent);

        // New intent was received so set it to use in onStart()
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        mLogger.Log("onStart");
        super.onStart();

        // If the app was launched by a different app, then get any parameters; otherwise use default settings
        Intent intent = getIntent();
        if(Display_Name.equalsIgnoreCase("")){
            mDisplayName.setText(intent.hasExtra("displayName") ? intent.getStringExtra("displayName") : "");
        }else{
            mDisplayName.setText(Display_Name);
        }
        if(ResourceID.equalsIgnoreCase("")){
            mResourceId.setText(intent.hasExtra("resourceId") ? intent.getStringExtra("resourceId") : "");
        }else{
            mResourceId.setText(ResourceID);
        }

        mReturnURL = intent.hasExtra("returnURL") ? intent.getStringExtra("returnURL") : null;
        mHideConfig = intent.getBooleanExtra("hideConfig", false);
        mAutoJoin = intent.getBooleanExtra("autoJoin", false);
        mAllowReconnect = intent.getBooleanExtra("allowReconnect", true);
        mCameraPrivacy = intent.getBooleanExtra("cameraPrivacy", false);
        mMicrophonePrivacy = intent.getBooleanExtra("microphonePrivacy", false);
        mEnableDebug = intent.getBooleanExtra("enableDebug", false);
        mExperimentalOptions = intent.hasExtra("experimentalOptions") ? intent.getStringExtra("experimentalOptions") : null;

        mLogger.Log("onStart: hideConfig = " + mHideConfig + ", autoJoin = " + mAutoJoin + ", allowReconnect = " + mAllowReconnect + ", enableDebug = " + mEnableDebug);

        // Enable toggle connect button
        mToggleConnectButton.setEnabled(true);

        // Hide the controls if hideConfig enabled
        if (mHideConfig) {
            mControlsLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        mLogger.Log("onResume");
        super.onResume();

        ViewTreeObserver viewTreeObserver = mVideoFrame.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mVideoFrame.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // If the vidyo connector was not previously successfully constructed then construct it

                    if (mVidyoConnector == null) {

                        if (mVidyoClientInitialized) {

                            try {
                                mVidyoConnector = new Connector(mVideoFrame,
                                        Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                                        15,
                                        "info@VidyoClient info@VidyoConnector warning",
                                        "",
                                        0);

                                // Set the client version in the toolbar
                                mClientVersion.setText("FaceMask 1.0.0" );//+ mVidyoConnector.getVersion()

                                // If enableDebug is configured then enable debugging
                                if (mEnableDebug) {
                                    mVidyoConnector.enableDebug(7776, "warning info@VidyoClient info@VidyoConnector");
                                    mClientVersion.setVisibility(View.VISIBLE);
                                }

                                // If cameraPrivacy is configured then mute the camera
                                if (mCameraPrivacy) {
                                    ToggleButton tb = (ToggleButton)findViewById(R.id.cameraPrivacyButton);
                                    tb.performClick();
                                }

                                // If microphonePrivacy is configured then mute the microphone
                                if (mMicrophonePrivacy) {
                                    ToggleButton tb = (ToggleButton)findViewById(R.id.microphonePrivacyButton);
                                    tb.performClick();
                                }

                                // Set experimental options if any exist
                                if (mExperimentalOptions != null) {
                                    ConnectorPkg.setExperimentalOptions(mExperimentalOptions);
                                }

                                // Set initial position
                                RefreshUI();

                                // Register for network interface callbacks
                                if (!mVidyoConnector.registerNetworkInterfaceEventListener(mSelf)) {
                                    mLogger.Log("VidyoConnector RegisterNetworkInterfaceEventListener failed");
                                }

                                // Register for log callbacks
                                if (!mVidyoConnector.registerLogEventListener(mSelf, "info@VidyoClient info@VidyoConnector warning")) {
                                    mLogger.Log("VidyoConnector RegisterLogEventListener failed");
                                }

                                // If configured to auto-join, then simulate a click of the toggle connect button
                                if (mAutoJoin) {
                                    mToggleConnectButton.performClick();
                                }
                            }
                            catch (Exception e) {
                                mLogger.Log("VidyoConnector Construction failed");
                                mLogger.Log(e.getMessage());
                            }
                        } else {
                            mLogger.Log("ERROR: VidyoClientInitialize failed - not constructing VidyoConnector ...");
                        }

                        Logger.getInstance().Log("onResume: mVidyoConnectorConstructed => " + (mVidyoConnector != null ? "success" : "failed"));
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        mLogger.Log("onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        mLogger.Log("onRestart");
        super.onRestart();
        if (mVidyoConnector != null) {
            mVidyoConnector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);
        }
    }

    @Override
    protected void onStop() {
        mLogger.Log("onStop");
        if (mVidyoConnector != null) {
            mVidyoConnector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mLogger.Log("onDestroy");

        // Release device resources
        mVidyoConnector.disable();
        mVidyoConnector = null;

        // Uninitialize the VidyoClient library
        ConnectorPkg.uninitialize();

        super.onDestroy();
    }

    // The device interface orientation has changed
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mLogger.Log("onConfigurationChanged");
        super.onConfigurationChanged(newConfig);

        // Refresh the video size after it is painted
        ViewTreeObserver viewTreeObserver = mVideoFrame.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mVideoFrame.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // Width/height values of views not updated at this point so need to wait
                    // before refreshing UI

                    RefreshUI();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /*
     * Private Utility Functions
     */

    // Refresh the UI
    private void RefreshUI() {
        // Refresh the rendering of the video
        mVidyoConnector.showViewAt(mVideoFrame, 0, 0, mVideoFrame.getWidth(), mVideoFrame.getHeight());
        mLogger.Log("VidyoConnectorShowViewAt: x = 0, y = 0, w = " + mVideoFrame.getWidth() + ", h = " + mVideoFrame.getHeight());
    }

    // The state of the VidyoConnector connection changed, reconfigure the UI.
    // If connected, dismiss the controls layout
    private void ConnectorStateUpdated(VIDYO_CONNECTOR_STATE state, final String statusText) {
        mLogger.Log("ConnectorStateUpdated, state = " + state.toString());

        mVidyoConnectorState = state;

        // Execute this code on the main thread since it is updating the UI layout

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // Update the toggle connect button to either start call or end call image
                mToggleConnectButton.setChecked(mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_CONNECTED);

                // Set the status text in the toolbar
                mToolbarStatus.setText(statusText);

                if (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_CONNECTED) {
                    if (!mHideConfig) {
                        // Update the view to hide the controls
                        mControlsLayout.setVisibility(View.GONE);
                    }
                } else {
                    // VidyoConnector is disconnected

                    // Display toolbar in case it is hidden
                    mToolbarLayout.setVisibility(View.VISIBLE);

                    // If a return URL was provided as an input parameter, then return to that application
                    if (mReturnURL != null) {
                        // Provide a callstate of either 0 or 1, depending on whether the call was successful
                        Intent returnApp = getPackageManager().getLaunchIntentForPackage(mReturnURL);
                        returnApp.putExtra("callstate", (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_DISCONNECTED) ? 1 : 0);
                        startActivity(returnApp);
                    }

                    // If the allow-reconnect flag is set to false and a normal (non-failure) disconnect occurred,
                    // then disable the toggle connect button, in order to prevent reconnection.
                    if (!mAllowReconnect && (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_DISCONNECTED)) {
                        mToggleConnectButton.setEnabled(false);
                        mToolbarStatus.setText("Call ended");
                    }

                    if (!mHideConfig ) {
                        // Update the view to display the controls
                        mControlsLayout.setVisibility(View.VISIBLE);
                    }
                }

                // Hide the spinner animation
                mConnectionSpinner.setVisibility(View.INVISIBLE);
            }
        });
    }

    /*
     * Button Event Callbacks
     */

    // The Connect button was pressed.
    // If not in a call, attempt to connect to the backend service.
    // If in a call, disconnect.
    public void ToggleConnectButtonPressed(View v) {
        if (mToggleConnectButton.isChecked()) {
            // Abort the Connect call if resourceId is invalid. It cannot contain empty spaces or "@".
            if (mResourceId.getText().toString().contains(" ") || mResourceId.getText().toString().contains("@") ||
                    mResourceId.getText().toString().equalsIgnoreCase("")) {
                mResourceId.setError("Invalid Resource ID");
                mToolbarStatus.setText("Invalid Resource ID");
                mToggleConnectButton.setChecked(false);
            }
            else if(mDisplayName.getText().toString().equalsIgnoreCase("")){
                mDisplayName.setError("Invalid Display Name");
                mToolbarStatus.setText("Invalid Display Name");
                mToggleConnectButton.setChecked(false);
            }

            else {
                mToolbarStatus.setText("Connecting...");

                // Display the spinner animation
                mConnectionSpinner.setVisibility(View.VISIBLE);

               GenerateToken.Generate(mDisplayName.getText().toString(), new TokenSuccess() {
                   @Override
                   public void Token(String token) {
                       String Host = "prod.vidyo.io";
                       final boolean status = mVidyoConnector.connect(
                               Host,
                               token,
                               mDisplayName.getText().toString(),
                               mResourceId.getText().toString().toLowerCase(),
                               MainActivity.this);
                       if (!status) {
                           // Hide the spinner animation
                           mConnectionSpinner.setVisibility(View.INVISIBLE);
                           ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_CONNECTION_FAILURE, "Connection failed");
                       }
                       prefs.edit().putString("DisplayName",mDisplayName.getText().toString()).apply();
                       prefs.edit().putString("ResourceID",mResourceId.getText().toString().toLowerCase()).apply();
                       mLogger.Log("VidyoConnectorConnect status = " + status);
                       Log.e(TAG,"Status = " +status);
                   }
               });
            }
        } else {
            // The button just switched to the callStart image: The user is either connected to a resource
            // or is in the process of connecting to a resource; call VidyoConnectorDisconnect to either disconnect
            // or abort the connection attempt.
            // Change the button back to the callEnd image because do not want to assume that the Disconnect
            // call will actually end the call. Need to wait for the callback to be received
            // before swapping to the callStart image.
            mToggleConnectButton.setChecked(true);

            mToolbarStatus.setText("Disconnecting...");

            mVidyoConnector.disconnect();
        }
    }


    // Toggle the microphone privacy
    public void MicrophonePrivacyButtonPressed(View v) {
        mVidyoConnector.setMicrophonePrivacy(((ToggleButton) v).isChecked());
    }

    // Toggle the camera privacy
    public void CameraPrivacyButtonPressed(View v) {
        mVidyoConnector.setCameraPrivacy(((ToggleButton) v).isChecked());
    }

    // Handle the camera swap button being pressed. Cycle the camera.
    public void CameraSwapButtonPressed(View v) {
        mVidyoConnector.cycleCamera();
    }

    // Toggle debugging
    public void ToggleDebugButtonPressed(View v) {
        mEnableDebug = !mEnableDebug;
        if (mEnableDebug) {
            mVidyoConnector.enableDebug(7776, "warning info@VidyoClient info@VidyoConnector");
            mClientVersion.setVisibility(View.VISIBLE);
        } else {
            mVidyoConnector.disableDebug();
            mClientVersion.setVisibility(View.INVISIBLE);
        }
    }

    // Toggle visibility of the toolbar
    @Override
    public void OnVideoFrameClicked() {
        if (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_CONNECTED) {
            if (mToolbarLayout.getVisibility() == View.VISIBLE) {
                mToolbarLayout.setVisibility(View.INVISIBLE);
            } else {
                mToolbarLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    /*
     *  Connector Events
     */

    // Handle successful connection.
    @Override
    public void onSuccess() {
        mLogger.Log("onSuccess: successfully connected.");
        ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_CONNECTED, "Connected");
    }

    // Handle attempted connection failure.
    @Override
    public void onFailure(Connector.ConnectorFailReason reason) {
        mLogger.Log("onFailure: connection attempt failed, reason = " + reason.toString());
        Log.e(TAG,"Connection Failed: "+ reason.toString());

        // Update UI to reflect connection failed
        ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_CONNECTION_FAILURE, "Connection failed");
    }

    // Handle an existing session being disconnected.
    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason reason) {
        if (reason == Connector.ConnectorDisconnectReason.VIDYO_CONNECTORDISCONNECTREASON_Disconnected) {
            mLogger.Log("onDisconnected: successfully disconnected, reason = " + reason.toString());
            ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_DISCONNECTED, "Disconnected");
        } else {
            mLogger.Log("onDisconnected: unexpected disconnection, reason = " + reason.toString());
            ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_DISCONNECTED_UNEXPECTED, "Unexpected disconnection");
        }
    }

    // Handle a message being logged.
    @Override
    public void onLog(LogRecord logRecord) {
        mLogger.LogClientLib(logRecord.name + " : " + logRecord.level + " : " + logRecord.functionName + " : " + logRecord.message);
    }

    @Override
    public void onNetworkInterfaceAdded(NetworkInterface vidyoNetworkInterface) {
        mLogger.Log("onNetworkInterfaceAdded: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily());
    }

    @Override
    public void onNetworkInterfaceRemoved(NetworkInterface vidyoNetworkInterface) {
        mLogger.Log("onNetworkInterfaceRemoved: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily());
    }

    @Override
    public void onNetworkInterfaceSelected(NetworkInterface vidyoNetworkInterface, NetworkInterface.NetworkInterfaceTransportType vidyoNetworkInterfaceTransportType) {
        mLogger.Log("onNetworkInterfaceSelected: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily());
    }

    @Override
    public void onNetworkInterfaceStateUpdated(NetworkInterface vidyoNetworkInterface, NetworkInterface.NetworkInterfaceState vidyoNetworkInterfaceState) {
        mLogger.Log("onNetworkInterfaceStateUpdated: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily() + " state=" + vidyoNetworkInterfaceState);
    }
}
