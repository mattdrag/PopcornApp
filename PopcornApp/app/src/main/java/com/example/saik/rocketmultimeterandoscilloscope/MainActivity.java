package com.example.saik.rocketmultimeterandoscilloscope;


    import android.app.ProgressDialog;
    import android.content.BroadcastReceiver;
    import android.content.ComponentName;
    import android.content.Context;
    import android.content.IntentFilter;
    import android.content.ServiceConnection;
    import android.os.IBinder;
    import android.support.annotation.LayoutRes;
    import android.support.annotation.NonNull;
    import android.support.annotation.Nullable;
    import android.support.v4.content.LocalBroadcastManager;
    import android.support.v7.app.AppCompatActivity;
    import android.os.Bundle;
    import android.content.Intent;
    import android.util.Log;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.AdapterView;
    import android.widget.ArrayAdapter;
    import android.widget.ListView;
    import android.bluetooth.BluetoothAdapter;
    import android.bluetooth.BluetoothDevice;
    import android.widget.TextView;
    import android.widget.Toast;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Set;


public class MainActivity extends AppCompatActivity {

    ////////////////////////Class global variables////////////////////////
    //widgets
    ListView deviceList;

    BTService btService = null;
    boolean isBound = false; //check to see if this client is bound to a service

    //misc
    private static final String TAG = "rocket";
    private ProgressDialog progress;
    public static final String RECEIVE_JSON = "com.bubblewall.saik.bubblewall.RECEIVE_JSON";
    LocalBroadcastManager broadcastManager;

    List<BluetoothDevice> listOfPairedDevices = new ArrayList<>();
    /////////////////////////////////////////////////////////////////////


    private BroadcastReceiver bReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if(intent.getAction().equals(RECEIVE_JSON))
            {
                String serviceJsonString = intent.getStringExtra("btConnectionStatusUpdate");
                if(serviceJsonString.equals("SuccessfulConnection"))
                {
                    progress.dismiss();
                    Log.d(TAG,"MainActivity: bReceiver: got message indicating that bluetooth connection is established.");
                    Log.d(TAG, "Going to menu activity");

                    Intent panelControlIntent = new Intent(MainActivity.this, MenuActivity.class);
                    startActivity(panelControlIntent);

                }
                else if(serviceJsonString.equals("FailedConnection"))
                {
                    progress.dismiss();
                    Log.d(TAG,"MainActivity: bReceiver: got message indicating that bluetooth connection failed.");
                    Toast.makeText(getApplicationContext(), "Unable to establish connection. Please make sure that the device is available", Toast.LENGTH_LONG).show();


                    //Restarts the application
                    Intent i = getBaseContext().getPackageManager()
                            .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        broadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_JSON);
        broadcastManager.registerReceiver(bReceiver, intentFilter);


        //Aquiring widgets
        deviceList = (ListView)findViewById(R.id.listView);


        if(BluetoothAdapter.getDefaultAdapter() == null) //if device does not have BT capabilities, abort the app
        {
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
            finish(); //finish the app
        }
        else
        {
            if(!BluetoothAdapter.getDefaultAdapter().isEnabled()) //if BT is present but is turned off
            {
                Log.d(TAG,"MainActivity: onCreate: Ask user to turn on BT.");
                //Ask to the user turn the bluetooth on
                Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBTon, 1);
            }
            else
            {
                Log.d(TAG,"MainActivity: onCreate: Looking for paired bt devices.");
                pairedDevicesList();
            }
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        Log.d(TAG,"MainActivity: onStop: Nothing to do.");
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if(isBound){
            unbindService(btServiceConnection);
        }


        stopService(new Intent(getBaseContext(), BTService.class)); //destroy the service so that bt will shut down
        broadcastManager.unregisterReceiver(bReceiver);
        isBound = false;
        Log.d(TAG,"MainActivity: onDestroy: stopping btCommunicationService.");
        Log.d(TAG,"MainActivity: onDestroy: stopped, bReceiver unregistered.");
    }

    //this method is called when the user clicks on paired devices button
    private void pairedDevicesList()
    {
        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices(); //get all bonded devices

        for(BluetoothDevice bt : pairedDevices) {
            listOfPairedDevices.add(bt);
        }


        ArrayList<String> list = new ArrayList<>();

        if (pairedDevices.size()>0)
        {
            for(BluetoothDevice bt : pairedDevices)
            {
                list.add(bt.getName()+bt.getAddress()); //Get the device's name and the address
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        ///////////////////////////////////////////////////////////////////
        DeviceListAdapter deviceListAdapter = new DeviceListAdapter(getApplicationContext(), R.layout.main_screen_listview_layout, list);
        deviceList.setAdapter(deviceListAdapter);
        deviceList.setOnItemClickListener(myListClickListener);
        ///////////////////////////////////////////////////////////////////


    }

    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick(AdapterView<?> av, View v, int index, long arg3)
        {
            String address = listOfPairedDevices.get(index).getAddress();

            //Start service in background to handle BT communication
            Intent btCommunicationCreate = new Intent(getBaseContext(), BTService.class);
            btCommunicationCreate.putExtra("BT_ADDRESS", address); //pass address to btCommuncationService
            bindService(btCommunicationCreate, btServiceConnection, Context.BIND_AUTO_CREATE);
            startService(btCommunicationCreate);

            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }
    };


    //This helps to create custom ListView on main screen
    private class DeviceListAdapter extends ArrayAdapter<String>
    {
        private List<String> deviceList;
        private int resource;
        private LayoutInflater inflater;
        DeviceListAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<String> objects)
        {
            super(context, resource, objects);
            deviceList = objects;
            this.resource = resource;
            inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
        {
            if(convertView == null)
            {
                convertView = inflater.inflate(resource, null);
            }

            TextView listText = (TextView) convertView.findViewById(R.id.main_screen_listview_layout_list_text);

            listText.setText(deviceList.get(position).substring(0,deviceList.get(position).length() - 17));

            return convertView;
        }
    }


    ////////////////////////AUX methods and classes////////////////////////

    //btCommunicationService connection handling
    private ServiceConnection btServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            BTService.MyLocalBinder binder = (BTService.MyLocalBinder) service; //this is a refference to a binder class in btCommunicationService
            btService = binder.getService();
            isBound = true;
            Log.d(TAG,"MainActivity: btServiceConnection: btService is established.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            isBound = false;
            Log.d(TAG,"MainActivity: btServiceConnection: btService is disconnected.");
        }
    };

}
