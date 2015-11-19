/*
Started from 11/3/2015
Course: EECS 397 Mobile Computing
Project 4 Edison Tutorial

Team 5: Junchao, Matt, James
*/

package com.example.team5.androidclientforedison;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.Buffer;
import java.text.SimpleDateFormat;
import java.util.Date;

//for Google api and location services
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;


public class MainActivity extends Activity implements
        ConnectionCallbacks,OnConnectionFailedListener,LocationListener
{
    private static final String TAG = "MainActivity";

    private Context context;


    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    public BluetoothAdapter mBluetoothAdapter;
    public BluetoothChatService mChatService = null;
    private int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;

    //making your app location aware
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    public Location mCurrentLocation;
    public double mLongitude;
    public double mLatitude;
    public LocationRequest mLocationRequest;
    public boolean mRequestingLocationUpdates = true;
    public String HomeLocation;



    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;
    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;


    private File Assignment5dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM+"/Assignment5");
    private String HW2name = "Homework2.csv";
    private String HW3name = "Homework3.cov";

    private File Homework2 = new File(Assignment5dir,HW2name);
    private File Homework3 = new File(Assignment5dir,HW3name);

    private String HW2CSV_header = "TimeStamp,Temperature,Humidity,Light,UV,Radon\n";
    private String HW3CSV_header = "TimeStamp,Temperature,Humidity,Light,UV,Radon,Home Location\n";





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!Assignment5dir.exists()) {
            Assignment5dir.mkdirs();

            if (!Homework2.exists()) {
                try {
                    Homework2.createNewFile();
                    BufferedWriter bfw1 = new BufferedWriter(new FileWriter(Homework2, true));
                    bfw1.write(HW2CSV_header);
                    bfw1.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!Homework3.exists()){
                try {
                    Homework3.createNewFile();
                    BufferedWriter bfw = new BufferedWriter(new FileWriter(Homework3,true));
                    bfw.write(HW3CSV_header);
                    bfw.close();
                } catch (IOException e){
                    e.printStackTrace();
                }
            }

        }

        mConversationView = (ListView) findViewById(R.id.in);
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mSendButton = (Button) findViewById(R.id.button_send);

        mConversationView.setVisibility(View.INVISIBLE);
        mOutEditText.setVisibility(View.INVISIBLE);
        mSendButton.setVisibility(View.INVISIBLE);

        //Build google api client
        BuildGoogleApiClient();
        creatLocationRequest();

    }

    //1. access GoogleApiClient, new GoogleApiClient.Builder(this)
    //2. creat LocationRequest mLocationRequest
    //3. Request location updates in onConnected(Bundle bundle) method
    protected synchronized void BuildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    protected void creatLocationRequest(){
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(2000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onLocationChanged(Location location){
        mCurrentLocation = location;
        mLongitude = mCurrentLocation.getLongitude();
        mLatitude = mCurrentLocation.getLatitude();
        HomeLocation = "("+mLongitude+","+mLatitude+")";
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mRequestingLocationUpdates){
            startLocationUpdates();
        }
    }

    //startLocationUpdates in onResume() and onConnected()
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    //stopLocationUpdates in onPause()
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,this);
    }


    @Override
    protected void onStart() {
        super.onStart();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter==null) {
            //Show an AlertDialog with message "Bluetooth is not supported on this device"
        } else {
            if (!mBluetoothAdapter.isEnabled()){
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        setupChat();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (mChatService!=null){
            mChatService.stop();
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        stopLocationUpdates();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
        mGoogleApiClient.connect();
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget

                    TextView textView = (TextView) findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    sendMessage(message);

            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    //set subtitle
    private void setStatus(int resId) {

        final ActionBar actionBar = this.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    private void setStatus(CharSequence subTitle) {

        final ActionBar actionBar = this.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }


    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.android_client_for_fdison, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }

            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }

            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,Intent data){
        switch(requestCode){
            case REQUEST_CONNECT_DEVICE_SECURE:{
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            }
            case REQUEST_CONNECT_DEVICE_INSECURE: {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }


    private void ensureDiscoverable() {
        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
        startActivity(discoverableIntent);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    mConversationView.setVisibility(View.INVISIBLE);
                    mOutEditText.setVisibility(View.INVISIBLE);
                    mSendButton.setVisibility(View.INVISIBLE);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            mConversationView.setVisibility(View.VISIBLE);
                            mSendButton.setVisibility(View.VISIBLE);
                            mOutEditText.setVisibility(View.VISIBLE);
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String mrowInput = timestamp+","+readMessage+"\n";
                    try {

                        BufferedWriter bfw = new BufferedWriter(new FileWriter(Homework3, true));
                        bfw.write(mrowInput);
                        bfw.close();
                    } catch (IOException e){
                        e.printStackTrace();
                    }

                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    context = getApplicationContext();
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                        Toast.makeText(context, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    context = getApplicationContext();
                    String toast = msg.getData().getString(Constants.TOAST);
                        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };




    @Override
    public void onConnectionSuspended(int cause){

    }

    @Override
    public void onConnectionFailed(ConnectionResult result){

    }


}

