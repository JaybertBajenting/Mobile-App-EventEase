package com.example.eventease;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private Button scan_btn;
    private Button refresh_event_btn;
    private TextView textView;
    private TextView eventInfoText;
    private ImageView eventImageView;
    private TextView eventNameText;
    private TextView eventDescriptionText;
    private static int idEvent = 0;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
        requestQueue = Volley.newRequestQueue(this);

        getCurrentEvent();
    }

    private void initializeViews() {
        scan_btn = findViewById(R.id.scanner);
        refresh_event_btn = findViewById(R.id.refresh_event);
        textView = findViewById(R.id.text);
        eventNameText = findViewById(R.id.eventNameText);
        eventDescriptionText = findViewById(R.id.eventDescriptionText);
        eventImageView = findViewById(R.id.eventImageView);
    }

    private void setupListeners() {
        scan_btn.setOnClickListener(v -> initiateScan());
        refresh_event_btn.setOnClickListener(v -> getCurrentEvent());
    }

    private void initiateScan() {
        IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
        intentIntegrator.setOrientationLocked(true);
        intentIntegrator.setPrompt("Scan user QR Code");
        intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        intentIntegrator.setCaptureActivity(CaptureActivityPortrait.class);
        intentIntegrator.initiateScan();
        intentIntegrator.setBeepEnabled(false);
    }


    private void getCurrentEvent() {
        Log.d("GetCurrentEvent", "Starting getCurrentEvent");
        String url = "https://eventease-oor7.onrender.com/api/v1/auth/event/getEventNow";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                this::handleEventResponse,
                error -> {
                    Log.e("EventDebug", "Error fetching event: " + error.getMessage());
                    showNoEventMessage("No Event Available At This Time");
                }
        ) {
            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                try {
                    String jsonString = new String(response.data, HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                    return Response.success(new JSONObject(jsonString), HttpHeaderParser.parseCacheHeaders(response));
                } catch (Exception e) {
                    return Response.error(new ParseError(e));
                }
            }
        };

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(jsonObjectRequest);
    }

    private void handleEventResponse(JSONObject response) {
        Log.d("ServerResponse", "Full response: " + response.toString());
        if (response.has("eventName")) {
            try {
                String eventName = response.getString("eventName");
                String eventDescription = response.optString("eventDescription", "No description available");
                int eventId = response.getInt("id");
                idEvent = eventId;

                updateEventUI(eventName, eventDescription);
                loadEventImage(eventId);
            } catch (Exception e) {
                Log.e("EventDebug", "Error parsing event data: " + e.getMessage(), e);
                showNoEventMessage("Error parsing event data");
            }
        } else {
            Log.d("EventDebug", "No eventName in response");
            showNoEventMessage("No events at this time");
        }
    }

    private void updateEventUI(String eventName, String eventDescription) {
        runOnUiThread(() -> {
            try {
                eventNameText.setText(eventName);
                eventDescriptionText.setText(eventDescription);
                findViewById(R.id.eventCard).setVisibility(View.VISIBLE);
                Log.d("GetCurrentEvent", "UI updated successfully");
            } catch (Exception e) {
                Log.e("GetCurrentEvent", "Error updating UI", e);
            }
        });
    }



    private void showNoEventMessage(String message) {
        runOnUiThread(() -> {
            eventNameText.setText(message);
            eventDescriptionText.setText("");
            eventImageView.setImageResource(android.R.color.transparent);
            findViewById(R.id.eventCard).setVisibility(View.VISIBLE);
        });
    }

    private void loadEventImage(int eventId) {
        String imageUrl = "https://eventease-oor7.onrender.com/api/v1/auth/event/getEventPicture/" + eventId;

        ImageRequest imageRequest = new ImageRequest(imageUrl,
                bitmap -> runOnUiThread(() -> {
                    eventImageView.setImageBitmap(bitmap);
                    Log.d("ImageDebug", "Image loaded successfully, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                }),
                0, 0, ImageView.ScaleType.CENTER_CROP, null,
                error -> runOnUiThread(() -> {
                    eventImageView.setImageResource(android.R.color.transparent);
                    Log.e("ImageDebug", "Error loading image: " + error.getMessage());
                }));

        requestQueue.add(imageRequest);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (intentResult != null) {
            String qrContent = intentResult.getContents();
            if (qrContent != null) {
                makePostRequest(qrContent);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void makePostRequest(String extractedValue) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        String currentPhTime = sdf.format(new Date());

        String url = "https://eventease-oor7.onrender.com/api/v1/auth/admin/attend/" + idEvent + "/" + extractedValue + "/?attendanceDate=" + currentPhTime;

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.d("NetworkResponse", "Success: " + response);
                    String message = "Attendance submitted successfully for " + extractedValue + "!";
                    handleSuccessResponse(response, message, extractedValue);
                },
                this::handleErrorResponse
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private void handleSuccessResponse(String response, String message, String extractedValue) {
        Log.d("SuccessResponse", "Entering handleSuccessResponse");
        mHandler.post(() -> {
            try {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                textView.setText("Success: " + extractedValue);
                eventNameText.setText(message);

                mHandler.postDelayed(() -> new Thread(this::getCurrentEvent).start(), 100);

                Log.d("SuccessResponse", "handleSuccessResponse completed");
            } catch (Exception e) {
                Log.e("UIUpdate", "Error updating UI: " + e.getMessage(), e);
            }
        });
    }

    private void handleErrorResponse(VolleyError error) {
        Log.e("NetworkError", "Error: " + error.getMessage());
        runOnUiThread(() -> {
            try {
                Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                textView.setText("User not found or error occurred");
            } catch (Exception e) {
                Log.e("UIUpdate", "Error updating UI: " + e.getMessage());
            }
        });
    }
}