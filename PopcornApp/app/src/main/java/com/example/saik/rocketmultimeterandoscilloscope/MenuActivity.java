package com.example.saik.rocketmultimeterandoscilloscope;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;


public class MenuActivity extends AppCompatActivity {


    Button btnPopcorn1, btnPopcorn2, btnPopcornDebug, customPopcornButton;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        //setup the number pickers
        String[] numbers = new String[500];
        for(int i =0 ; i < 500 ; i++) {
            numbers[i] = i + "";
        }
        final NumberPicker numPicker1 = (NumberPicker) findViewById(R.id.numberPicker);
        numPicker1.setMaxValue(500);
        numPicker1.setMinValue(1);
        numPicker1.setDisplayedValues(numbers);
        numPicker1.setValue(41); // the starting number. +1 bc of 0 base
        numPicker1.setBackgroundColor(getResources().getColor(android.R.color.white));


        final NumberPicker numPicker2 = (NumberPicker) findViewById(R.id.numberPicker2);
        numPicker2.setMaxValue(500);
        numPicker2.setMinValue(1);
        numPicker2.setDisplayedValues(numbers);
        numPicker2.setValue(31); //the starting number. +1 bc of 0 base
        numPicker2.setBackgroundColor(getResources().getColor(android.R.color.white));

        //setup buttons
        btnPopcorn1 = (Button) findViewById(R.id.btn_popcorn_1);
        btnPopcorn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                //TODO: setup popular kernal numbers
                int popSettingsArray[] = {100,90};
                myIntent.putExtra("pop_settings_arr", popSettingsArray);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        btnPopcorn2 = (Button) findViewById(R.id.btn_popcorn_2);
        btnPopcorn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                //TODO: setup popular kernal numbers
                int popSettingsArray[] = {150,135};
                myIntent.putExtra("pop_settings_arr", popSettingsArray);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        btnPopcornDebug = (Button) findViewById(R.id.btn_popcorn_3);
        btnPopcornDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                int popSettingsArray[] = {100,90};
                myIntent.putExtra("pop_settings_arr", popSettingsArray);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        customPopcornButton = (Button) findViewById(R.id.customPopButton);
        customPopcornButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                int[] popSettingsArray = new int[2];
                popSettingsArray[0] = numPicker1.getValue() - 1; //-1 bc of array indexing
                popSettingsArray[1] = numPicker2.getValue() - 1; //-1 bc of array indexing
                myIntent.putExtra("pop_settings_arr", popSettingsArray);
                MenuActivity.this.startActivity(myIntent);
            }
        });
    }


    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
