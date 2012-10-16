/**
 *   Copyright 2011, 2012 Guenther Hoelzl
 *
 *   This file is part of MINDdroid.
 *
 *   MINDdroid is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   MINDdroid is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with MINDdroid.  If not, see <http://www.gnu.org/licenses/>.
**/

package com.example.visualnxt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.visualnxt.BTCommunicator;
import com.example.visualnxt.R;
import com.example.visualnxt.LCPMessage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;



/** 
 * This class is for uploading programs to the NXT brick via Bluetooth.
 * Special programs will be able to communicate with MINDdroid, so no PC
 * is required for playing with a robot.
 */
public class UniversalUploader extends Activity implements UploadThreadListener, DialogListener, BTConnectable
{
    private static final int DIALOG_NXT = 0;
    private static final int DIALOG_FILE = 1;

    // preinstalled modules on res/raw directoy
    private static String[] preinstalledFilesString = new String[] 
    	{ "Square.nxj",
        "Block.rxe",
        "Linefollower.rxe",
        "NXTCounter.rxe",
        "Pong.rxe",
        "Robogator_4.rxe"
      };

    private BTCommunicator mNXT;
	private UploadThread uploadThread;
	private Handler handler;
    private ProgressDialog progressDialog;
    private int uploadStatus;
    private int runningDialog;
    public static final int UPDATE_TIME = 200;
    public static final int MENU_TOGGLE_CONNECT = Menu.FIRST;
    public static final int MENU_START_SW = Menu.FIRST + 1;
    public static final int MENU_QUIT = Menu.FIRST + 2;
    public static final int MENU_UPLOAD = Menu.FIRST + 3;
    
    public static final int ACTION_BUTTON_SHORT = 0;
    public static final int ACTION_BUTTON_LONG = 1;
    
    private static final int REQUEST_CONNECT_DEVICE = 1000;
    private static final int REQUEST_ENABLE_BT = 2000;
    private BTCommunicator myBTCommunicator = null;
    private boolean connected = false;
    private ProgressDialog connectingProgressDialog;
    private Handler btcHandler;
    private Menu myMenu;
    private Activity thisActivity;
    private boolean btErrorPending = false;
    private boolean pairing;
    private static boolean btOnByUs = false;
    int mRobotType;
    int motorLeft;
    private int directionLeft; // +/- 1
    int motorRight;
    private boolean stopAlreadySent = false;
    private int directionRight; // +/- 1
    private int motorAction;
    private int directionAction; // +/- 1
    private List<String> programList;
    private static final int MAX_PROGRAMS = 20;
    private String programToStart;
    private Toast reusableToast;
    
    
        
    /** 
     * Called when the activity is first created. 
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        initLayout();
        // Create objects for communication
        mNXT = new BTCommunicator(this, null, 
            BluetoothAdapter.getDefaultAdapter(), getResources());
        handler = new Handler();   
        // Create and launch the upload thread
        uploadThread = new UploadThread(this, getResources());
        uploadThread.setBluetoothCommunicator(mNXT);
        uploadThread.start();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    /** 
     * Called when the activity is destroyed. 
     */
    @Override
	protected void onDestroy() {
		super.onDestroy();
				
		// request the uploadthread to stop
		uploadThread.requestStop();	
    }

    /**
     * Asks if bluetooth was switched on during the runtime of the app. For saving 
     * battery we switch it off when the app is terminated.
     * @return true, when bluetooth was switched on by the app
     */
    public static boolean isBtOnByUs() {
        return btOnByUs;
    }

    /**
     * Sets a flag when bluetooth was switched on durin runtime
     * @param btOnByUs true, when bluetooth was switched on by the app
     */
    public static void setBtOnByUs(boolean btOnByUs) {
        UniversalUploader.btOnByUs = btOnByUs;
    }

    /**
     * @return true, when currently pairing 
     */
    public boolean isPairing() {
        return pairing;
    }

    /**
     * Displays a message as a toast
     */        
    public void showToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
        return;
    }

    /**
     * Displays a message from resID as a toast
     */        
    public void showToast(int resID) {
        Toast toast = Toast.makeText(this, resID, Toast.LENGTH_SHORT);
        toast.show();
        return;
    }

    /**
     * Displays resp. updates a progress dialog
     */        
    public void showProgressDialog(String message, int maxProgress, int currentProgress) {
        boolean initialized = false;
        if (progressDialog == null) {
            initialized = true;
            progressDialog = new ProgressDialog(this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.setMessage(message);
        }
        progressDialog.setMax(maxProgress);
        progressDialog.setProgress(currentProgress);
        if (initialized) 
            progressDialog.show();
    }

    /**
     * Displays resp. updates a progress dialog
     */        
    public void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
            progressDialog.setMessage(message);
            progressDialog.show();
        }
    }

    /**
     * Dismisses an existing progress dialog
     */        
    public void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
            
    /**
     * Initializes the values on the main screen
     */    
    private void initLayout() {
        initNXTButton();
        initFileButton();
        initUploadButton();
    }

    /**
     * Initializes the "SELECT NXT" button
     */        
    private void initNXTButton() {
        Button fileButton = (Button) findViewById(R.id.nxt_button);
        fileButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                selectNXT();

            }
        }); 
    }

    /**
     * Initializes the "SELECT FILE" button
     */        
    private void initFileButton() {
        Button fileButton = (Button) findViewById(R.id.file_button);
        fileButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showFileDialog();
            }
        }); 
    }

    /**
     * Starts the NXT selection activity
     */            
    private void selectNXT() {
    	
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
    
    private void startBTCommunicator(String mac_address) {
        connected = false;        
        connectingProgressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.connecting_please_wait), true);

        if (myBTCommunicator != null) {
            try {
                myBTCommunicator.destroyNXTconnection();
            }
            catch (IOException e) { }
        }
        createBTCommunicator();
        myBTCommunicator.setMACAddress(mac_address);
        myBTCommunicator.start();
    }
    
    
    
    private void createBTCommunicator() {
        // interestingly BT adapter needs to be obtained by the UI thread - so we pass it in in the constructor
        myBTCommunicator = new BTCommunicator(this, myHandler, BluetoothAdapter.getDefaultAdapter(), getResources());
        btcHandler = myBTCommunicator.getHandler();
    }

    /**
     * Shows the file dialog
     */            
    private void showFileDialog() {
        UploaderFileDialog fileDialog = 
            new UploaderFileDialog(this, this);
        if (fileDialog.refreshFileList(preinstalledFilesString) == 0) 
            showToast(R.string.uul_no_files);
        else {
            runningDialog = DIALOG_FILE;
            fileDialog.show();
        }
    }

    /* 
     * This is the method for returning values of dialogs
     * @param the selected text
     */
    public void dialogUpdate(String text) {
        TextView textView;
        switch (runningDialog) {
            case DIALOG_NXT:
                textView = (TextView) findViewById(R.id.nxt_name);
                textView.setText(text);
                break;

            case DIALOG_FILE:
                textView = (TextView) findViewById(R.id.uul_file_name);
                textView.setText(text);
                break;
        }
    }

    /**
     * Initializes the "UPLOAD" button
     */        
    private void initUploadButton() {
        Button uploadButton = (Button) findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {                
                TextView nxtTextView = (TextView) findViewById(R.id.nxt_name);
                String macAddress = nxtTextView.getText().toString();
                if (macAddress.compareTo("") == 0) {
                    showToast(R.string.uul_please_select_nxt);
                    return;
                }
                macAddress = macAddress.substring(macAddress.lastIndexOf('-')+1);
                TextView uulTextView = (TextView) findViewById(R.id.uul_file_name);
                String fileName = uulTextView.getText().toString();
                if (fileName.compareTo("") == 0) {
                    showToast(R.string.uul_please_select_file);
                    return;
                }
                uploadThread.enqueueUpload(macAddress, fileName); 
            }
        }); 
    }

    /**
     * This will be called by the UploadThread to signal an update of the 
     * current status. 
     * @param status The current state of the UploadThread
     */        
	public void handleUploadThreadUpdate(final int status) {
		handler.post(new Runnable() {
			public void run() {
                if (status != uploadStatus) {
                    dismissProgressDialog();
                    uploadStatus = status;
                }
                showUploadStatus();
			}
		});
	}

    /**
     * Shows the current status of the uploader either in 
     * a progress bar or in toast in case of an error.
     */        
    private void showUploadStatus() {

        switch (uploadStatus) {
            case UploadThread.CONNECTING:
                showProgressDialog(getResources().getString(R.string.uul_connecting));
                break;
            case UploadThread.UPLOADING:
                showProgressDialog(getResources().getString(R.string.uul_uploading), 
                    uploadThread.getFileLength(), 
                    uploadThread.getBytesUploaded());
                break;
            default:
                dismissProgressDialog();
        }

        switch (uploadThread.getErrorCode()) {
            case UploadThread.NO_ERROR:
                break;
            case UploadThread.OPEN_BT_ERROR:
                if (pairing)
                    showToast(R.string.uul_bluetooth_pairing);
                else
                    showBTErrorDialog();
                break;
            case UploadThread.CLOSE_BT_ERROR:
                showBTErrorDialog();
                break;
            case UploadThread.OPEN_FILE_ERROR:
                showToast(R.string.uul_file_open_error);
                break;
            case UploadThread.UPLOAD_ERROR:
                showBTErrorDialog();
                break;
            default:
                showToast(R.string.uul_other_error);
        }
        uploadThread.resetErrorCode();
    }

    /**
     * Shows an error dialog when there's an error regarding
     * bluettooth transfer.
     */        
    private void showBTErrorDialog() {
        if (btErrorPending == false) {
            btErrorPending = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.bt_error_dialog_title))
            .setMessage(getResources().getString(R.string.bt_error_dialog_message)).setCancelable(false)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    btErrorPending = false;
                    dialog.cancel();
                }
            });
            builder.create().show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device infos
                    String infos = data.getExtras().getString(DeviceListActivity.DEVICE_NAME_AND_ADDRESS);
                    pairing = data.getExtras().getBoolean(DeviceListActivity.PAIRING);
                    TextView textView = (TextView) findViewById(R.id.nxt_name);
                    textView.setText(infos);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        btOnByUs = true;
                        break;
                    case Activity.RESULT_CANCELED:
                        showToast(R.string.bt_needs_to_be_enabled);
                        finish();
                        break;
                }
                break;
        }                
    }
    
    /**
     * Displays a message as a toast
     * @param textToShow the message
     * @param length the length of the toast to display
     */
    private void showToast(String textToShow, int length) {
        reusableToast.setText(textToShow);
        reusableToast.setDuration(length);
        reusableToast.show();
    }
    
    final Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message myMessage) {
            switch (myMessage.getData().getInt("message")) {
                case BTCommunicator.DISPLAY_TOAST:
                    showToast(myMessage.getData().getString("toastText"), Toast.LENGTH_SHORT);
                    break;
                case BTCommunicator.STATE_CONNECTED:
                    connected = true;
                    programList = new ArrayList<String>();
                    connectingProgressDialog.dismiss();
                    sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.GET_FIRMWARE_VERSION, 0, 0);
                    break;
                case BTCommunicator.MOTOR_STATE:

                    if (myBTCommunicator != null) {
                        byte[] motorMessage = myBTCommunicator.getReturnMessage();
                        int position = byteToInt(motorMessage[21]) + (byteToInt(motorMessage[22]) << 8) + (byteToInt(motorMessage[23]) << 16)
                                       + (byteToInt(motorMessage[24]) << 24);
                        showToast(getResources().getString(R.string.current_position) + position, Toast.LENGTH_SHORT);
                    }

                    break;

                case BTCommunicator.STATE_CONNECTERROR_PAIRING:
                    connectingProgressDialog.dismiss();
                    destroyBTCommunicator();
                    break;

                case BTCommunicator.STATE_CONNECTERROR:
                    connectingProgressDialog.dismiss();
                case BTCommunicator.STATE_RECEIVEERROR:
                case BTCommunicator.STATE_SENDERROR:

                    destroyBTCommunicator();
                    if (btErrorPending == false) {
                        btErrorPending = true;
                        // inform the user of the error with an AlertDialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(thisActivity);
                        builder.setTitle(getResources().getString(R.string.bt_error_dialog_title))
                        .setMessage(getResources().getString(R.string.bt_error_dialog_message)).setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                btErrorPending = false;
                                dialog.cancel();
                                selectNXT();
                            }
                        });
                        builder.create().show();
                    }

                    break;

                case BTCommunicator.FIRMWARE_VERSION:

                    if (myBTCommunicator != null) {
                        byte[] firmwareMessage = myBTCommunicator.getReturnMessage();
                        // check if we know the firmware
                        boolean isLejosMindDroid = true;
                        for (int pos=0; pos<4; pos++) {
                            if (firmwareMessage[pos + 3] != LCPMessage.FIRMWARE_VERSION_LEJOSMINDDROID[pos]) {
                                isLejosMindDroid = false;
                                break;
                            }
                        }
                        // afterwards we search for all files on the robot
                        sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.FIND_FILES, 0, 0);
                    }

                    break;

                case BTCommunicator.FIND_FILES:

                    if (myBTCommunicator != null) {
                        byte[] fileMessage = myBTCommunicator.getReturnMessage();
                        String fileName = new String(fileMessage, 4, 20);
                        fileName = fileName.replaceAll("\0","");

                        if (fileName.endsWith(".nxj") || fileName.endsWith(".rxe")) {
                            programList.add(fileName);
                        }

                        // find next entry with appropriate handle, 
                        // limit number of programs (in case of error (endless loop))
                        if (programList.size() <= MAX_PROGRAMS)
                            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.FIND_FILES,
                                           1, byteToInt(fileMessage[3]));
                    }

                    break;
                    
                case BTCommunicator.PROGRAM_NAME:
                    if (myBTCommunicator != null) {
                        byte[] returnMessage = myBTCommunicator.getReturnMessage();
                        startRXEprogram(returnMessage[2]);
                    }
                    
                    break;
                
                    
                case BTCommunicator.VIBRATE_PHONE:
                    if (myBTCommunicator != null) {
                        byte[] vibrateMessage = myBTCommunicator.getReturnMessage();
                        Vibrator myVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        myVibrator.vibrate(vibrateMessage[2]*10);
                    }
                    
                    break;
            }
        }
    };
    
    /**
     * Sends the message via the BTCommuncator to the robot.
     * @param delay time to wait before sending the message.
     * @param message the message type (as defined in BTCommucator)
     * @param value1 first parameter
     * @param value2 second parameter
     */   
    void sendBTCmessage(int delay, int message, int value1, int value2) {
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", message);
        myBundle.putInt("value1", value1);
        myBundle.putInt("value2", value2);
        Message myMessage = myHandler.obtainMessage();
        myMessage.setData(myBundle);

        if (delay == 0)
            btcHandler.sendMessage(myMessage);

        else
            btcHandler.sendMessageDelayed(myMessage, delay);
    }

    /**
     * Sends the message via the BTCommuncator to the robot.
     * @param delay time to wait before sending the message.
     * @param message the message type (as defined in BTCommucator)
     * @param String a String parameter
     */       
    void sendBTCmessage(int delay, int message, String name) {
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", message);
        myBundle.putString("name", name);
        Message myMessage = myHandler.obtainMessage();
        myMessage.setData(myBundle);

        if (delay == 0)
            btcHandler.sendMessage(myMessage);
        else
            btcHandler.sendMessageDelayed(myMessage, delay);
    }
    
    private int byteToInt(byte byteValue) {
        int intValue = (byteValue & (byte) 0x7f);

        if ((byteValue & (byte) 0x80) != 0)
            intValue |= 0x80;

        return intValue;
    }
    
    /**
     * Sends a message for disconnecting to the communcation thread.
     */
    public void destroyBTCommunicator() {

        if (myBTCommunicator != null) {
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.DISCONNECT, 0, 0);
            myBTCommunicator = null;
        }

        connected = false;
    }
    /**
     * Depending on the status (whether the program runs already) we stop it, wait and restart it again.
     * @param status The current status, 0x00 means that the program is already running.
     */   
    public void startRXEprogram(byte status) {
        if (status == 0x00) {
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.STOP_PROGRAM, 0, 0);
            sendBTCmessage(1000, BTCommunicator.START_PROGRAM, programToStart);
        }    
        else {
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.START_PROGRAM, programToStart);
        }
    } 

}
