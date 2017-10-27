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
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static com.example.saik.rocketmultimeterandoscilloscope.MainActivity.RECEIVE_JSON;

public class OscilloscopeActivity extends AppCompatActivity {

    int counter = 0;
    List<DataPoint> collectionOfPoints;
    DataPoint[] dataPoint;
    boolean startSweep = false;
    long timer = 0;
    double voltage1old = 0;
    double triggerPoint = 0;

    GraphView graph;

    private final int POSITIVE_SLOPE_TRIGGER = 0;
    private final int NEGATIVE_SLOPE_TRIGGER = 1;
    private final int FALLING_EDGE_TRIGGER = 2;

    int triggerType = POSITIVE_SLOPE_TRIGGER;

    Button increaseX, decreaseX, choosePositiveSlopeTrigger, choooseNegativeSlopeTrigger, chooseFallingEdgeTrigger;

    double voltDataBT1;
    Handler handler;
    private LineGraphSeries<DataPoint> series;

    private static final String TAG = "rocket";
    BTService btService;
    boolean isBound = false;

    BtDataListener btDataListener = null;

    private static String incomingData = "";

    LocalBroadcastManager broadcastManager;

    TextView triggerPositionValue;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_oscilloscope);
        voltDataBT1 = 0;
        triggerPositionValue = (TextView) findViewById(R.id.trigger_value_text);

        final Runnable updater = new Runnable() {
            @Override
            public void run() {
                //updateSeries();
                handler.postDelayed(this, 10); //100ms delay
            }
        };
        handler.post(updater);

        //setup buttons
        increaseX = (Button) findViewById(R.id.increaseX);
        increaseX.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(triggerPoint <= 5)
                {
                    triggerPoint +=0.1;
                    triggerPositionValue.setText(new DecimalFormat("#.#").format(triggerPoint));
                    clearGraph();
                }
            }
        });

        decreaseX = (Button) findViewById(R.id.decreaseX);
        decreaseX.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(triggerPoint >= -5)
                {
                    triggerPoint -=0.1;
                    triggerPositionValue.setText(new DecimalFormat("#.#").format(triggerPoint));
                    clearGraph();
                }
            }
        });


        choosePositiveSlopeTrigger = (Button) findViewById(R.id.button_positive_slope_trigger);
        choosePositiveSlopeTrigger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerType = POSITIVE_SLOPE_TRIGGER;
                clearGraph();
                choosePositiveSlopeTrigger.setBackgroundResource(android.R.color.holo_blue_light);
                choooseNegativeSlopeTrigger.setBackgroundResource(android.R.drawable.btn_default);
                chooseFallingEdgeTrigger.setBackgroundResource(android.R.drawable.btn_default);
            }
        });


        choooseNegativeSlopeTrigger = (Button) findViewById(R.id.button_negative_slope_trigger);
        choooseNegativeSlopeTrigger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerType = NEGATIVE_SLOPE_TRIGGER;
                clearGraph();
                choosePositiveSlopeTrigger.setBackgroundResource(android.R.drawable.btn_default);
                choooseNegativeSlopeTrigger.setBackgroundResource(android.R.color.holo_blue_light);
                chooseFallingEdgeTrigger.setBackgroundResource(android.R.drawable.btn_default);
            }
        });


        chooseFallingEdgeTrigger = (Button) findViewById(R.id.button_falling_edge_trigger);
        chooseFallingEdgeTrigger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                triggerType = FALLING_EDGE_TRIGGER;
                clearGraph();
                choosePositiveSlopeTrigger.setBackgroundResource(android.R.drawable.btn_default);
                choooseNegativeSlopeTrigger.setBackgroundResource(android.R.drawable.btn_default);
                chooseFallingEdgeTrigger.setBackgroundResource(android.R.color.holo_blue_light);
            }
        });


        //setup graph
        // we get graph view instance
        graph = (GraphView) findViewById(R.id.ampmeter);
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
        viewport.setMinY(-3);
        viewport.setMaxY(3);
        viewport.setScrollable(true);
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(60);

        graph.getGridLabelRenderer().setGridColor(Color.WHITE);
        graph.getGridLabelRenderer().setVerticalAxisTitle("Voltage");
        graph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
        graph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
        graph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
        graph.getGridLabelRenderer().setNumHorizontalLabels(10);


        collectionOfPoints = new ArrayList<>();
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

                    if(!completeMessage.equals(""))
                    {
                        if(!completeMessage.contains("$"))
                        {
                            voltage1old = voltDataBT1;

                            voltDataBT1 = ( Integer.parseInt(completeMessage) /204.8) - 2.5;

                            if(triggerType == POSITIVE_SLOPE_TRIGGER)
                            {
                                if(!startSweep && voltDataBT1 > triggerPoint - 0.1 && voltDataBT1 < triggerPoint + 0.1 && (voltDataBT1 > voltage1old ))
                                {
                                    startSweep = true;
                                    timer = System.currentTimeMillis();
                                    collectionOfPoints.clear();
                                    counter = 0;
                                }
                            }
                            else if(triggerType == NEGATIVE_SLOPE_TRIGGER)
                            {
                                if(!startSweep && voltDataBT1 > triggerPoint - 0.1 && voltDataBT1 < triggerPoint + 0.1 && (voltDataBT1 < voltage1old ))
                                {
                                    startSweep = true;
                                    timer = System.currentTimeMillis();
                                    collectionOfPoints.clear();
                                    counter = 0;
                                }
                            }
                            else if(triggerType == FALLING_EDGE_TRIGGER)
                            {
                                if(!startSweep && voltDataBT1 > voltage1old)
                                {
                                    startSweep = true;
                                    timer = System.currentTimeMillis();
                                    collectionOfPoints.clear();
                                    counter = 0;
                                }
                            }

                            if(startSweep)
                            {
                                if(System.currentTimeMillis() - timer >= 200)
                                {
                                    startSweep = false;
                                    updateDataPoints(collectionOfPoints.toArray(new DataPoint[0]));
                                    publishProgress();
                                }


                                if(counter == 0)
                                {
                                    collectionOfPoints.add(new DataPoint(0, voltDataBT1));
                                    counter++;
                                }

                                else
                                {
                                    collectionOfPoints.add(new DataPoint(collectionOfPoints.get(counter - 1).getX() + (1), voltDataBT1));
                                    counter++;
                                }
                            }
                        }
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
            updateGraph(dataPoint);
        }
    }

    private void updateDataPoints(DataPoint[] dp)
    {
        dataPoint = new DataPoint[dp.length];
        dataPoint = dp;
    }


    private void clearGraph(){
        graph.removeAllSeries();
        series = new LineGraphSeries<>();
        graph.addSeries(series);

        series.setTitle("Oscilloscope");
        series.setColor(Color.YELLOW);
        series.setThickness(2);

        Viewport viewport = graph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(60);
    }


    //For graphing points
    private void updateGraph(DataPoint points[]) {

        graph.removeAllSeries();
        series = new LineGraphSeries<>(points);
        graph.addSeries(series);

        //style
        // styling series
        series.setTitle("Oscilloscope");
        series.setColor(Color.YELLOW);
        series.setThickness(2);

        Viewport viewport = graph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        Log.d(TAG, "length of points is " + points.length);
        viewport.setMaxX(60);
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
        Intent gattServiceIntent = new Intent(OscilloscopeActivity.this, BTService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(btBroadcastReceiver, makeGattUpdateIntentFilter());

        broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(btBroadcastReceiver, makeGattUpdateIntentFilter());
    }
}
