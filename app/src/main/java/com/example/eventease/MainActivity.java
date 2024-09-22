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
import android.widget.LinearLayout;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Button scan_btn;
    private Button refresh_event_btn;
    private TextView textView;
    private TextView eventNameText;
    private TextView eventDescriptionText;
    private TextView eventTypeText;
    private ImageView eventImageView;
    private ImageView profilePictureView;
    private TextView userFullNameText;
    private LinearLayout userInfoLayout;
    private Button approveButton;
    private Button rejectButton;
    private Button timeoutButton;

    private static int idEvent = 0;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private RequestQueue requestQueue;
    private String scannedUsername;



    private static String ipAddress = "https://eventease-q2yh.onrender.com";





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
        //textView = findViewById(R.id.text);
        eventNameText = findViewById(R.id.eventNameText);
        eventDescriptionText = findViewById(R.id.eventDescriptionText);
        eventTypeText = findViewById(R.id.eventTypeText);
        eventImageView = findViewById(R.id.eventImageView);
        profilePictureView = findViewById(R.id.profilePictureView);
        userFullNameText = findViewById(R.id.userFullNameText);
        userInfoLayout = findViewById(R.id.userInfoLayout);
        approveButton = findViewById(R.id.approveButton);
        rejectButton = findViewById(R.id.rejectButton);
        timeoutButton = findViewById(R.id.timeoutButton);
    }

    private void setupListeners() {
        scan_btn.setOnClickListener(v -> initiateScan());
        refresh_event_btn.setOnClickListener(v -> getCurrentEvent());
        approveButton.setOnClickListener(v -> {
            if (scannedUsername != null && !scannedUsername.isEmpty()) {
              //  makePostRequest(scannedUsername);
            } else {
                Toast.makeText(MainActivity.this, "No user scanned", Toast.LENGTH_SHORT).show();
            }
        });
        rejectButton.setOnClickListener(v -> resetUserInfo());
        timeoutButton.setOnClickListener(v -> {
            if (scannedUsername != null && !scannedUsername.isEmpty()) {
                makePostRequest(scannedUsername, "timeout");
            } else {
                Toast.makeText(MainActivity.this, "No user scanned", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initiateScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setBeepEnabled(false);
        integrator.setPrompt("Scan a QR code");
        integrator.setOrientationLocked(true);
        integrator.setCaptureActivity(CaptureActivityPortrait.class);
        integrator.initiateScan();
    }

    private void getCurrentEvent() {
        Log.d("GetCurrentEvent", "Starting getCurrentEvent");
        String url =  ipAddress+"/api/v1/auth/event/getEventNow";

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
                getAttendanceCount(eventId);
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
            eventTypeText.setVisibility(View.GONE);
            eventImageView.setImageResource(android.R.color.transparent);
            findViewById(R.id.eventCard).setVisibility(View.VISIBLE);
        });
    }

    private void loadEventImage(int eventId) {
        String imageUrl = ipAddress+"/api/v1/auth/event/getEventPicture/" + eventId;

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

    private void getAttendanceCount(int eventId) {
        String url = ipAddress+"/api/v1/auth/admin/count/" + eventId;

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        int count = Integer.parseInt(response);
                        updateEventTypeUI(count);
                    } catch (NumberFormatException e) {
                        Log.e("AttendanceCount", "Error parsing response to integer", e);
                    }
                },
                error -> Log.e("AttendanceCount", "Error fetching attendance count: " + error.getMessage())
        );

        requestQueue.add(stringRequest);
    }

    private void updateEventTypeUI(int count) {
        String eventType = (count <= 2) ? "One Time Event" : "Multi-day Event";
        runOnUiThread(() -> {
            eventTypeText.setText(eventType);
            eventTypeText.setVisibility(View.VISIBLE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (intentResult != null) {
            String qrContent = intentResult.getContents();
            Log.d("QRScanResult", "QR Code content: " + qrContent);
            if (qrContent != null) {
                getUserByUsername(idEvent, qrContent);
            }
        } else if (resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra("attendSuccess", false)) {
                Toast.makeText(this, "Attendance recorded successfully", Toast.LENGTH_SHORT).show();
            } else if (data.getBooleanExtra("timeoutSuccess", false)) {
                Toast.makeText(this, "Timeout recorded successfully", Toast.LENGTH_SHORT).show();
            }
            getCurrentEvent();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void getUserByUsername(long eventId, String uuid) {
        String url = ipAddress+"/api/v1/auth/admin/getUserByUuid/" + eventId + "/" + uuid;
        Log.d("getUserByUsername", "Requesting URL: " + url);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    Log.d("UserResponse", "User data: " + response.toString());
                    try {
                        long userId = response.getLong("id");
                        String firstName = response.getString("firstName");
                        String lastName = response.getString("lastName");
                        String fullName = firstName + " " + lastName;

                        Intent intent = new Intent(MainActivity.this, UserInfoActivity.class);
                        intent.putExtra("userId", userId);
                        intent.putExtra("fullName", fullName);
                        intent.putExtra("uuid", uuid);
                        intent.putExtra("eventId", eventId);
                        startActivityForResult(intent, 1);
                    } catch (Exception e) {
                        Log.e("UserResponse", "Error parsing user data", e);
                    }
                },
                error -> {
                    Log.e("UserResponse", "Error: " + error.getMessage());
                    String errorMessage = "Error: User not found or not authorized";

                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        if (statusCode == 409) {
                            errorMessage = "User not joined to this event or attendance already checked";

                            try {
                                String responseBody = new String(error.networkResponse.data, "UTF-8");
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                String detailedMessage = jsonResponse.getString("messages");
                                errorMessage =  detailedMessage;
                            } catch (UnsupportedEncodingException | JSONException e) {
                                Log.e("UserResponse", "Error parsing response body", e);
                            }
                        }
                    }

                    final String finalErrorMessage = errorMessage;
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, finalErrorMessage, Toast.LENGTH_LONG).show();
                        resetUserInfo();
                    });
                }
        );

        requestQueue.add(jsonObjectRequest);
    }

    private void makePostRequest(String extractedValue, String action) {
        // This method is now handled in UserInfoActivity
        // You can remove it from MainActivity if it's no longer used here
    }

    private void resetUserInfo() {
        userInfoLayout.setVisibility(View.GONE);
        scannedUsername = null;
        profilePictureView.setImageResource(android.R.color.transparent);
        userFullNameText.setText("");
    }
}