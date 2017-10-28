package com.example.saik.rocketmultimeterandoscilloscope;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


public class MenuActivity extends AppCompatActivity {


    Button btnPopcorn1, btnPopcorn2, btnPopcorn3, btnAmpmeter, btnOscilloscope;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);


        //setup buttons
        btnPopcorn1 = (Button) findViewById(R.id.btn_popcorn_1);
        btnPopcorn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                myIntent.putExtra("kernals", 50);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        btnPopcorn2 = (Button) findViewById(R.id.btn_popcorn_2);
        btnPopcorn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                myIntent.putExtra("kernals", 100);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        btnPopcorn3 = (Button) findViewById(R.id.btn_popcorn_3);
        btnPopcorn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                myIntent.putExtra("kernals", 150);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        btnAmpmeter = (Button) findViewById(R.id.btn_ampmeter);
        btnAmpmeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                myIntent.putExtra("kernals", 200);
                MenuActivity.this.startActivity(myIntent);
            }
        });

        btnOscilloscope = (Button) findViewById(R.id.btn_oscilloscope);
        btnOscilloscope.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MenuActivity.this, PopcornActivity.class);
                myIntent.putExtra("kernals", 250);
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
