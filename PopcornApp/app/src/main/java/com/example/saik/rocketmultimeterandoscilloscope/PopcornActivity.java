package com.example.saik.rocketmultimeterandoscilloscope;

//import is degenerate
import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import java.util.Vector;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.widget.Toast;

import java.text.DecimalFormat;

public class PopcornActivity extends AppCompatActivity {

    Intent intent;
    int[] popSettings;
    int numOfKernals, numToBePopped;

    private static String TAG = "Popcorn";
    private static final int RECORD_REQUEST_CODE = 101;

    TextView display_txt, pop_phase_txt, ambient_txt, num_kernal_txt, num_popped_txt;
    boolean isRecording = false;
    Handler handler;
    private LineGraphSeries<DataPoint> series;
    private float lastX = 0;

    AudioMeter audioMeter = new AudioMeter();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_popcorn);

        //Grab intent and extras
        intent = getIntent();
        //If we want a feature for counting pops
        popSettings = intent.getIntArrayExtra("pop_settings_arr");
        numOfKernals = popSettings[0];
        numToBePopped = popSettings[1];

        // Request microphone permission
        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        }


        // Set up UI
        num_kernal_txt = (TextView) findViewById(R.id.numKernals);
        ambient_txt = (TextView) findViewById(R.id.ambient);
        pop_phase_txt = (TextView) findViewById(R.id.popPhase);
        display_txt = (TextView) findViewById(R.id.decibelMeter);
        display_txt.setText(new DecimalFormat("##.##").format(0) + " dbs");
        num_kernal_txt = (TextView) findViewById(R.id.numKernals);
        num_kernal_txt.setText("kernals = " + numOfKernals + " toBePopped = " +numToBePopped);
        num_popped_txt = (TextView) findViewById(R.id.numPopped);
        num_popped_txt.setText("numPopped : " + 0);

        final Button recTog = (Button) findViewById(R.id.startButton);
        recTog.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isRecording){
                    isRecording = false;
                    recTog.setText("Record");
                    audioMeter.stop();
                }
                else{
                    isRecording = true;
                    recTog.setText("Pause");
                    audioMeter.start();
                    pop_phase_txt.setText("PRE_POP"); //update UI
                }
            }
        });

        ////////////////////////////////////////
        ////// Main thread for doing work //////
        ////////////////////////////////////////

        final Runnable updater = new Runnable() {
            //  Timing: 100-120ms for addEntry() call, sleeps for 10ms between calls.
            boolean didStart = false;
            long startTimeInMillis; // for switching states

            //  States:
            //  PRE_POP:    create ranges for ambient noise over 5,
            //              changes state after 10s min of recording
            //  POP_PHASE:  count number of pops
            //              changes state when we've reached numToBePopped number of pops
            //  POST_POP:   Final state
            //              triggers some alarm for user to notify them popcorn is done
            byte POP_STATE = 0; //[ PRE_POP, POP_PHASE, POST_POP ] ==> [ 0, 1, 2 ]
            Vector<Double> VAmbientNoise = new Vector<>(1000, 100); //For determining ambient noise in PRE_POP
            int numPops;    // For setting didStartPopping in POP_PHASE
            // boolean didStartPopping = false; // Current implementation: we start popping after PRE_POP phase
            long popInterval = 0; // The time in milliseconds between pop audio samples.
            long timeOfLastPop = 0;
            boolean didFinish = false; // For playing alarm one time in POST_POP


            //These bounds will be defined during the PRE_POP and POP_PHASE
            //NOTE: Some threshold of noise will be too much for app to distinguish pops.
            //      we predefine AMBIENT_MAX to be the maximum ambient
            //      range the popcorn sensor can operate at
            final int AMBIENT_MAX = 0; //TODO
            double AMBIENT_NOISE_UPPER_BOUND = 0.0;

            @Override
            public void run() {
                if (isRecording) { //Start button has been pressed
                    //Grab the current system time in milliseconds if we didn't already start yet
                    if (!didStart){
                        startTimeInMillis = System.currentTimeMillis();
                        didStart = true;
                    }

                    //Do work depending on which phase we are in
                    ////////////// PRE_POP //////////////
                    if (POP_STATE == 0){
                        //TODO: For now, after 10 seconds, advance phase
                        long deltaTime = System.currentTimeMillis() - startTimeInMillis;
                        if (deltaTime >= 10000){    // Finish condition
                            updateDisplay();
                            //Set AMBIENT_NOISE_UPPER_BOUND
                            AMBIENT_NOISE_UPPER_BOUND = determineAmbient();
                            ambient_txt.setText(""+AMBIENT_NOISE_UPPER_BOUND); //update UI
                            POP_STATE = 1; // Enter POP_PHASE
                            pop_phase_txt.setText("POP_PHASE"); //update UI
                        }
                        else{                       // Rest of the loops
                            VAmbientNoise.add(updateDisplay());
                        }
                    }

                    ////////////// POP_PHASE  //////////////
                    else if (POP_STATE == 1){
                        // We need to determine when the popcorn begins to pop. Any audio sample that
                        // is above the AMBIENT_NOISE_UPPER_BOUND can potentially be a pop, so that is a valid pop sample.
                        // When we receive a total of 20 valid pop samples,didStartPopping will be set to true
                        // and we initiate waiting for the interval to lengthen to 2s.
                        double audioReading = updateDisplay();

                        // Check if sound is a pop
                        if (audioReading > AMBIENT_NOISE_UPPER_BOUND){
                            //Sound is confirmed to be a pop
                            popInterval = System.currentTimeMillis() - timeOfLastPop;
                            timeOfLastPop = System.currentTimeMillis(); //Update timeOfLastPop for next iteration
                            numPops++;
                            num_popped_txt.setText("numPopped : " + numPops);

                            // Finish condition
                            if (numPops >= numToBePopped){
                                //The popcorn is done!
                                pop_phase_txt.setText("POST_POP"); //update UI
                                POP_STATE = 2; // Enter POST_POP
                            }
                        }
                    }

                    ////////////// POST_POP  //////////////
                    else{
                        //alarm user (should only play once)
                        if (!didFinish) {
                            Toast.makeText(PopcornActivity.this, "Hey your popcorn is done cool thx",
                                    Toast.LENGTH_LONG).show();
                            didFinish = true;
                        }
                    }
                    handler.postDelayed(this, 10); //100ms delay
                }
                else {      // Start button has not yet been pressed
                    handler.postDelayed(this, 10); //100ms delay
                }
            }

            //Updates all UI elements. helper for run() code
            public double updateDisplay(){
                double audioReading = addEntry();
                display_txt.setText(new DecimalFormat("##.##").format(audioReading) + " dbs");
                return(audioReading);  //Takes about 100-120ms
                                       //addEntry calls audioMeter.getAmplitude(), and returns the audio reading
            }

            // Called after the PRE_POP phase, gets statistics on data and fills in the AMBIENT_NOISE_UPPER_BOUND
            private double determineAmbient(){
                //Get the mean
                double sum = 0.0;
                for(double x : VAmbientNoise){
                    sum += x;
                }
                double mean = sum/VAmbientNoise.size();

                //Get the variance
                double temp = 0;
                for(double a : VAmbientNoise)
                    temp += (a-mean)*(a-mean);
                double var =  temp/(VAmbientNoise.size()-1);

                //Get the stdev
                double stdev = Math.sqrt(var);

                //Set AMBIENT_NOISE_UPPER_BOUND to the upper 95th percentile TODO: maybe 1 stdev?
                return (mean + (2*stdev));
            }
        };
        handler.post(updater);

        ////////////////////////////////////////
        ///// End of thread for doing work /////
        ////////////////////////////////////////


        //setup graph
        // we get graph view instance
        GraphView graph = (GraphView) findViewById(R.id.graph);
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
        viewport.setMaxY(30000);
        viewport.setScrollable(true);
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(10);

        graph.getGridLabelRenderer().setGridColor(Color.WHITE);
        graph.getGridLabelRenderer().setVerticalAxisTitle("Decibels");
        graph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
        graph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
        graph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
    }

    // Function for requesting mic permission
    protected void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_REQUEST_CODE);
    }

    // Function for graphing points
    private double addEntry() {
        double audioReading = audioMeter.getAmplitude();
        if (lastX < 10.0){
            series.appendData(new DataPoint(lastX, audioReading), false, 10000); // we can store 10000 values at a time
        }
        else{
            series.appendData(new DataPoint(lastX, audioReading), true, 10000); // we can store 10000 values at a time
        }
        lastX += 0.1; //Estimate to make timing on graph accurate to system time. Graph will roughly move along with real time, with some error.
        return audioReading;
    }

    //-----------------------------------------------------------------------------//
    //---------------------- Audio recording class --------------------------------//
    //-----------------------------------------------------------------------------//

    private class AudioMeter {

        private AudioRecord audioRecorder = null;
        private int minSize;

        public void start() {
            minSize= AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000,AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,minSize);
            audioRecorder.startRecording();
        }

        public void stop() {
            if (audioRecorder != null) {
                audioRecorder.stop();
            }
        }

        // TODO: We might have to change the way we get amplitude readings if we need a faster sample rate.
        // TODO: here's a reference : https://stackoverflow.com/questions/21986385/understanding-of-the-audio-recorder-read-buffer

        public double getAmplitude() {
            short[] buffer = new short[minSize];
            audioRecorder.read(buffer, 0, minSize);
            int max = 0;

            //  Bottleneck for sampling rate. Buffer is 512 bytes, we are looping 512 times and finding
            //  the max value.
            for (short s : buffer)
            {
                if (Math.abs(s) > max)
                {
                    max = Math.abs(s);
                }
            }

            return max;
        }
    }


    //Extras
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}