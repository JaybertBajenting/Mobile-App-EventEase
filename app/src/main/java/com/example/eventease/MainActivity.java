package com.example.eventease;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.HashMap;
import java.util.Map;
import com.android.volley.Request;
public class MainActivity extends AppCompatActivity {

    Button scan_btn;
    TextView textView;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scan_btn = findViewById(R.id.scanner);
        textView = findViewById(R.id.text);

        scan_btn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v){
                IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
                intentIntegrator.setOrientationLocked(true);
                intentIntegrator.setPrompt("Scan user Qr Code");
                intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
                intentIntegrator.initiateScan();


            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (intentResult != null) {
            String qrContent = intentResult.getContents();
            if (qrContent != null) {
                makePostRequest(qrContent);
                textView.setText("QR Code scanned: " + qrContent);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    private void makePostRequest(String extractedValue) {
        String url = "http://192.168.1.3:8080/api/v1/auth/admin/attend/" + extractedValue;
        //kol ilisi nang 192.168.1.3 sa ip sa imo laptop





        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {

                    Toast.makeText(MainActivity.this, "Attendance submitted successfully!\nResponse: " + response, Toast.LENGTH_LONG).show();
                },
                error -> {

                    Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                return params;
            }
        };


        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }

}