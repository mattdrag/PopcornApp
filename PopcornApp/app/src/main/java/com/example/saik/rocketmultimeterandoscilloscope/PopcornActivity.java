package com.example.saik.rocketmultimeterandoscilloscope;


import android.graphics.Color;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

    int voltDataBT;
    TextView display;
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

        display = (TextView) findViewById(R.id.voltmeter);
        voltDataBT = 0;
        display.setText(new DecimalFormat("##.##").format(0) + " dbs");
        final Button recTog = (Button) findViewById(R.id.ampmeterButton);
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
        final Runnable updater = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    display.setText(new DecimalFormat("##.##").format(audioMeter.getAmplitude()) + " dbs");
                    addEntry();
                    handler.postDelayed(this, 30); //1ms delay
                }
                else {
                    handler.postDelayed(this, 30); //1ms delay
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
        viewport.setMaxY(30000);
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

    //For graphing points
    private void addEntry() {
        if (lastX < 10.0){
            series.appendData(new DataPoint(lastX, audioMeter.getAmplitude()), false, 10000); // we can store 10000 values at a time
        }
        else{
            series.appendData(new DataPoint(lastX, audioMeter.getAmplitude()), true, 10000); // we can store 10000 values at a time
        }
        lastX+= 0.1;
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