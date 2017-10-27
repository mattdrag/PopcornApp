package com.example.saik.rocketmultimeterandoscilloscope;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DecimalFormat;

import static com.example.saik.rocketmultimeterandoscilloscope.MainActivity.RECEIVE_JSON;

public class Voltmeter0to50Activity extends AppCompatActivity {

    int voltDataBT;
    TextView display;
    boolean isVoltmeterOn = true;
    Handler handler;
    private LineGraphSeries<DataPoint> series;
    private float lastX = 0;

    private static final String TAG = "rocket";
    BTService btService;
    boolean isBound = false;

    BtDataListener btDataListener = null;

    private static String incomingData = "";

    LocalBroadcastManager broadcastManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_voltmeter0to50);

        display = (TextView) findViewById(R.id.voltmeter);
        voltDataBT = 0;
        display.setText(new DecimalFormat("##.##").format(calculateVoltageValue()) + " V");
        final Button voltTog = (Button) findViewById(R.id.ampmeterButton);
        voltTog.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isVoltmeterOn){
                    isVoltmeterOn = false;
                    voltTog.setText("Resume");
                }
                else{
                    isVoltmeterOn = true;
                    voltTog.setText("Pause");
                }
            }
        });
        final Runnable updater = new Runnable() {
            @Override
            public void run() {
                if (isVoltmeterOn) {
                    display.setText(new DecimalFormat("##.##").format(calculateVoltageValue()) + " V");
                    addEntry();
                    handler.postDelayed(this, 100); //100ms delay
                }
                else {
                    handler.postDelayed(this, 100); //100ms delay
                }
            }
        };
        handler.post(updater);


        //setup graph
        // we get graph view instance
        GraphView graph = (GraphView) findViewById(R.id.ampmeter);
        // data
        series = new LineGraphSeries<>();
        graph.addSeries(series);

        //style
        // styling series
        series.setTitle("Oscilloscope");
        series.setColor(Color.YELLOW);
        series.setThickness(2);

        // customize a little bit viewport
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(60);
        viewport.setScrollable(true);
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(10);

        graph.getGridLabelRenderer().setGridColor(Color.WHITE);
        graph.getGridLabelRenderer().setVerticalAxisTitle("Voltage");
        graph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
        graph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
        graph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
    }


    @Override
    protected void onPause() {
        super.onPause();
        tryUnregisteringReceiver();
        btDataListener.cancel(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MenuActivity:onResume: Binding Service, Registering Receiver");
        bindServiceAndRegisterReceiver();
        btDataListener = new BtDataListener();
        btDataListener.execute();
    }

    public void onBackPressed()
    {
        Log.d(TAG, "MenuActivity: onBackPressed: Go back to previous activity since back button is pressed.");
        cleanup();
        btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_EXIT.getValue());
        finish();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "VoltmeterActivity: onDestroy: calling cleanup");

        cleanup();
    }


    private void cleanup()
    {
        tryUnregisteringReceiver();

        if(btDataListener != null)
        {
            btDataListener.cancel(true);
        }
    }

    //For graphing points
    private void addEntry() {
        if (lastX < 10.0){
            series.appendData(new DataPoint(lastX, calculateVoltageValue()), false, 10000); // we can store 10000 values at a time
        }
        else{
            series.appendData(new DataPoint(lastX, calculateVoltageValue()), true, 10000); // we can store 10000 values at a time
        }
        lastX+= 0.1;
    }

    private double calculateVoltageValue(){
        if (voltDataBT !=0){
            return voltDataBT/20.48;
        }

        return 0;
    }

    //-----------------------------------------------------------------------------//
    //-------------Broadcast receiver and service binder stuff---------------------//
    //-----------------------------------------------------------------------------//


    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "remoteControlBubbleWall: mServiceConnection: onServiceConnected");
            btService = ((BTService.MyLocalBinder) service).getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "remoteControlBubbleWall: mServiceConnection: onServiceDisconnected");
            btService = null;
            isBound = false;
        }
    };


    private class BtDataListener extends AsyncTask<Void, Void, Void> {

        String completeMessage = "";
        String startSymbol = "*";
        String endSymbol = "|";


        @Override
        protected Void doInBackground(Void... devices)
        {

            while(!isCancelled())
            {
                if(btService != null)
                {
                    incomingData += btService.checkIncomingBtData();

                    int start = incomingData.indexOf(startSymbol);
                    int end = incomingData.indexOf(endSymbol);
                    int extraStartSymbol = incomingData.substring(start+1, incomingData.length()).indexOf(startSymbol);

                    if(start == -1 && end != -1)
                    {
                        incomingData = incomingData.substring(end + 1, incomingData.length());
                    }
                    else if(start != -1 && end != -1 && start > end)
                    {
                        incomingData = incomingData.substring(start, incomingData.length());
                    }
                    else if(start != -1 && end != -1 && (extraStartSymbol != -1 && extraStartSymbol < end))
                    {
                        incomingData = incomingData.substring(extraStartSymbol + 1, incomingData.length());
                    }
                    else if(start != -1 && end != -1)
                    {
                        completeMessage = incomingData.substring(start+1,end);
                        incomingData = incomingData.substring(end + 1,incomingData.length());
                    }

                    if(!completeMessage.equals("") && !completeMessage.contains("$") && !completeMessage.contains(","))
                    {
                        //Log.d(TAG, "MenuActivity: BtDataListener: Received data messages " + voltDataBT);
                        voltDataBT = Integer.parseInt(completeMessage);
                        completeMessage = "";
                    }
                }

            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values)
        {
            super.onProgressUpdate(values);
        }
    }


    private final BroadcastReceiver btBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals(RECEIVE_JSON)) {
                String serviceJsonString = intent.getStringExtra("btConnectionStatusUpdate");

                if(serviceJsonString.equals("ConnectionLost")) {
                    Log.d(TAG, "MenuActivity: btBroadcastReceiver: We lost connection with BT");
                    Toast.makeText(getApplicationContext(), "Connection with BT was lost", Toast.LENGTH_LONG).show();
                    cleanup();

                    //Restarts the application
                    Intent i = getBaseContext().getPackageManager()
                            .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                }
            }
        }

    };



    void tryUnregisteringReceiver() {
        try {
            unregisterReceiver(btBroadcastReceiver);
        }
        catch(IllegalArgumentException e) {
            Log.e(TAG, "Receiver was already unregistered");
        }

        if(isBound){
            unbindService(mServiceConnection);
        }
        isBound = false;
    }



    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_JSON);
        return intentFilter;
    }

    void bindServiceAndRegisterReceiver() {
        Intent gattServiceIntent = new Intent(Voltmeter0to50Activity.this, BTService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(btBroadcastReceiver, makeGattUpdateIntentFilter());

        broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(btBroadcastReceiver, makeGattUpdateIntentFilter());
    }
}