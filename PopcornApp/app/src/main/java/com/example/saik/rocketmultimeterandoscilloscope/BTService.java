package com.example.saik.rocketmultimeterandoscilloscope;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import android.os.Binder;

public class BTService extends Service {

    ////////////////////////Class global variables////////////////////////
    private boolean isBtConnected = false;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    String address;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private boolean ConnectSuccess = true;

    private final IBinder btCommunicationServiceBinder = new MyLocalBinder();

    //misc
    private static final String TAG = "rocket";

    private static InputStream mmInStream = null;

    enum BtMessageIn {
        BT_MESSAGE_IN_VOLTMETER_RANGE_0_TO_0_POINT_5(0), BT_MESSAGE_IN_VOLTMETER_RANGE_0_TO_10(1),
        BT_MESSAGE_IN_VOLTMETER_RANGE_0_TO_50(2), BT_MESSAGE_IN_AMPMETER(3), BT_MESSAGE_IN_OSCILLOSCOPE(4),
        BT_MESSAGE_IN_EXIT(5);

        private final int value;

        BtMessageIn(final int newValue) {
            value = newValue;
        }

        public int getValue() {
            return value;
        }
    }

    enum BtMessageOut {
        BT_MESSAGE_OUT_VOLTMETER_RANGE_0_TO_0_POINT_5(0), BT_MESSAGE_OUT_VOLTMETER_RANGE_0_TO_10(1),
        BT_MESSAGE_OUT_VOLTMETER_RANGE_0_TO_50(2), BT_MESSAGE_OUT_AMPMETER(3), BT_MESSAGE_OUT_OSCILLOSCOPE(4),
        BT_MESSAGE_OUT_EXIT(5);

        private final int value;

        BtMessageOut(final int newValue) {
            value = newValue;
        }

        public int getValue() {
            return value;
        }
    }

    /////////////////////////////////////////////////////////////////////

    public BTService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return btCommunicationServiceBinder;
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.d(TAG,"BTService: mReceiver: Connection Established");
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
            {
                Log.d(TAG,"BTService: mReceiver: Connection Lost");

                Intent RTReturn = new Intent(MainActivity.RECEIVE_JSON);
                RTReturn.putExtra("btConnectionStatusUpdate", "ConnectionLost");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(RTReturn);

                isBtConnected = false;
            }
        }
    };



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //Create Bluetooth Broadcast intent
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);

        final Intent intentForThread = intent;
        if(intentForThread.hasExtra("BT_ADDRESS"))
        {
            if(!intentForThread.getExtras().getString("BT_ADDRESS").equals(null))
            {
                address = (String) intentForThread.getExtras().get("BT_ADDRESS");
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Connection with BT was lost", Toast.LENGTH_LONG).show();

                Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(), "Connection with BT was lost", Toast.LENGTH_LONG).show();

            Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage( getBaseContext().getPackageName() );
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }

        //Create a new thread for this service
        Runnable r = new Runnable() {
            @Override
            public void run() {
                new ConnectBT().execute(); //Call the class to connect
            }
        };

        Thread btCommunicationThread = new Thread(r);
        btCommunicationThread.start();

        return Service.START_STICKY;
    }


    @Override
    public void onDestroy(){

        super.onDestroy();
        Log.d(TAG,"BTService: onDestroy: Destroyed");

        try {
            unregisterReceiver(mReceiver);
        }
        catch(IllegalArgumentException e) {
            Log.e(TAG, "Receiver was already unregistered");
        }
    }

    public void terminateBtConnection()
    {
        if(mmInStream != null)
        {
            try
            {
                Log.d(TAG,"BTService: btDataListener: Closing mmInStream");
                mmInStream.close();
                mmInStream = null;
            }
            catch (IOException e)
            {
                Log.d(TAG,"BTService: btDataListener: ERROR when closing the mmInStream. Error code = " + e);
            }
        }


        if (btSocket != null) //If the btSocket is busy
        {
            try
            {
                Log.d(TAG,"BTService: btDataListener: Closing btSocket");
                btSocket.close(); //close connection
                isBtConnected = false;
                btSocket = null;
            }
            catch (IOException e)
            {
                Log.d(TAG,"BTService: btDataListener: ERROR when closing the btSocket. Error code = " + e);
            }
        }
    }


    public boolean sendBtMessage(int message){
        if (btSocket!=null)
        {
            try {
                btSocket.getOutputStream().write(message);
                Log.d(TAG,"BTService: sendBtMessage: sending message " + message + ".");
            }
            catch (IOException e) {
                msg("Error");
                Log.d(TAG,"BTService: sendBtMessage: Error when sending message " + message + ".");
                return false;
            }
        }
        return true;
    }



    private class ConnectBT extends AsyncTask<Void, Void, Void>{

        private Intent RTReturn = new Intent(MainActivity.RECEIVE_JSON);

        @Override
        protected Void doInBackground(Void... devices){
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                    Log.d(TAG,"BTService: ConnectBT: Connection established");
                }
            }
            catch (IOException e){
                ConnectSuccess = false;//if the try failed, you can check the exception here
                Log.d(TAG,"BTService: ConnectBT: Failed to establish a bluetooth connection");
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result){

            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                Log.d(TAG,"BTService: ConnectBT: Connection postCheck failed");

                RTReturn.putExtra("btConnectionStatusUpdate", "FailedConnection");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(RTReturn);
                Log.d(TAG,"BTService: ConnectBT: Letting MainActivity know that connection failed");
            }
            else {
                msg("Connected.");
                Log.d(TAG,"BTService: ConnectBT: Connection postCheck success");
                isBtConnected = true;

                RTReturn.putExtra("btConnectionStatusUpdate", "SuccessfulConnection");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(RTReturn);
                Log.d(TAG,"BTService: ConnectBT: Letting MainActivity know that connection is established");


                //Creating input stream for socket
                try {
                    mmInStream = btSocket.getInputStream();
                }
                catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating input stream", e);
                }
            }
        }
    }


    String checkIncomingBtData()
    {
        if(mmInStream != null && isBtConnected)
        {
            try
            {
                if (mmInStream.available() > 0)
                {
                    byte[] buffer = new byte[1024];
                    int bytes = mmInStream.read(buffer);
                    return new String(buffer, 0, bytes);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                Log.d(TAG,"BTService: btDataListener: ERROR:: " + e);
            }
        }

        return "";
    }


    private void msg(String s) {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    class MyLocalBinder extends Binder {
        BTService getService()
        {
            return BTService.this;
        }
    }
}
