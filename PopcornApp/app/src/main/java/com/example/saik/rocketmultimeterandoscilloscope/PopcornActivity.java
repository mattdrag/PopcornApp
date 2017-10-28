package com.example.saik.rocketmultimeterandoscilloscope;


import android.Manifest;
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

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.text.DecimalFormat;

public class PopcornActivity extends AppCompatActivity {

    private static String TAG = "Popcorn";
    private static final int RECORD_REQUEST_CODE = 101;

    TextView display_txt, pop_phase_txt;
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

        // Request microphone permission
        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        }

        // Set up UI
        display_txt = (TextView) findViewById(R.id.decibelMeter);
        display_txt.setText(new DecimalFormat("##.##").format(0) + " dbs");
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
                }
            }
        });
        pop_phase_txt = (TextView) findViewById(R.id.popPhase);

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
            //  POP_PHASE:  determine amplitude of pops,
            //              begin recording interval between pops,
            //              changes state when interval between pops is >= 2seconds
            //  POST_POP:   Final state
            //              triggers some alarm for user to notify them popcorn is done
            byte POP_STATE = 0; //[ PRE_POP, POP_PHASE, POST_POP ] ==> [ 0, 1, 2 ]


            //These bounds will be defined during the PRE_POP and POP_PHASE
            //NOTE: Some threshold of noise will be too much for app to distinguish pops.
            //      we predefine AMBIENT_MAX to be the maximum ambient
            //      range the popcorn sensor can operate at
            final int AMBIENT_MAX = 0; //TODO
            int AMBIENT_NOISE_LOWER_BOUND = 0;
            int AMBIENT_NOISE_UPPER_BOUND = 0;
            int POPCORN_NOISE_LOWER_BOUND = 0;
            int POPCORN_NOISE_UPPER_BOUND = 0;

            @Override
            public void run() {
                if (isRecording) { //Start button has been pressed
                    //Grab the current system time in milliseconds if we didn't already start yet
                    if (!didStart){
                        startTimeInMillis = System.currentTimeMillis();
                        didStart = true;
                    }

                    //Do work depending on which phase we are in
                    if (POP_STATE == 0){ //PRE_POP
                        //TODO: For now, after 10 seconds, advance phase
                        long deltaTime = System.currentTimeMillis() - startTimeInMillis;
                        if (deltaTime >= 10000){
                            POP_STATE = 1;
                        }
                        updateDisplay();
                    }
                    else if (POP_STATE == 1){ //POP_PHASE
                        //TODO: For now, after 30 seconds, advance phase
                        long deltaTime = System.currentTimeMillis() - startTimeInMillis;
                        if (deltaTime >= 40000){
                            POP_STATE = 2;
                        }
                        updateDisplay();
                    }
                    else{ //POST_POP
                        updateDisplay();
                    }
                    handler.postDelayed(this, 10); //100ms delay
                }
                else {
                    handler.postDelayed(this, 10); //100ms delay
                }
            }

            //Updates all UI elements. helper for run() code
            public void updateDisplay(){
                display_txt.setText(new DecimalFormat("##.##").format(audioMeter.getAmplitude()) + " dbs");
                if (POP_STATE == 0){ //PRE_POP
                    pop_phase_txt.setText("PRE_POP");
                }
                else if (POP_STATE == 1){ //POP_PHASE
                    pop_phase_txt.setText("POP_PHASE");
                }
                else{ //POST_POP
                    pop_phase_txt.setText("POST_POP");
                }
                addEntry();  //Takes about 100-120ms
                             //addEntry calls audioMeter.getAmplitude(), which is the bottleneck
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

    //Function for graphing points
    private void addEntry() {
        if (lastX < 10.0){
            series.appendData(new DataPoint(lastX, audioMeter.getAmplitude()), false, 10000); // we can store 10000 values at a time
        }
        else{
            series.appendData(new DataPoint(lastX, audioMeter.getAmplitude()), true, 10000); // we can store 10000 values at a time
        }
        lastX += 0.125; //Estimates delay to make timing on graph accurate to system time. Graph will roughly move along with real time, with some error.
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