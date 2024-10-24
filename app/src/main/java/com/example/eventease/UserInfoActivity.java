package com.example.eventease;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class UserInfoActivity extends AppCompatActivity {
    private ImageView profilePictureView;
    private TextView userFullNameText;
    private Button approveButton;
    private Button timeoutButton;
    private Button rejectButton;
    private RequestQueue requestQueue;
    private long userId;
    private String username;

    private String uuid;
    private long eventId;


    private static String ipAddress = "https://king-prawn-app-92pca.ondigitalocean.app";




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_info);

        initializeViews();
        setupListeners();
        requestQueue = Volley.newRequestQueue(this);

        // Get data from intent
        Intent intent = getIntent();
        userId = intent.getLongExtra("userId", -1);
        String fullName = intent.getStringExtra("fullName");
        username = intent.getStringExtra("username");
        uuid = intent.getStringExtra("uuid");
        eventId = intent.getLongExtra("eventId", -1);

        userFullNameText.setText(fullName);
        getProfilePicture(userId);
    }

    private void initializeViews() {
        profilePictureView = findViewById(R.id.profilePictureView);
        userFullNameText = findViewById(R.id.userFullNameText);
        approveButton = findViewById(R.id.approveButton);
        timeoutButton = findViewById(R.id.timeoutButton);
        rejectButton = findViewById(R.id.rejectButton);
    }

    private void setupListeners() {
        approveButton.setOnClickListener(v -> makePostRequest(uuid, "attend"));
        timeoutButton.setOnClickListener(v -> makePostRequest(uuid, "timeout"));
        rejectButton.setOnClickListener(v -> finish());
    }


    private void getProfilePicture(long userId) {
        String url =  ipAddress + "/api/v1/auth/user/getProfilePicture/" + userId;
        Log.d("getProfilePicture", "Requesting URL: " + url);

        ImageRequest imageRequest = new ImageRequest(url,
                response -> {
                    Log.d("ProfilePicture", "Profile picture loaded successfully");
                    runOnUiThread(() -> {
                        profilePictureView.setImageBitmap(response);
                        profilePictureView.setVisibility(View.VISIBLE);
                    });
                },
                0, 0, null, Bitmap.Config.RGB_565,
                error -> {
                    Log.e("ProfilePicture", "Error loading profile picture: " + error.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(UserInfoActivity.this, "Error loading profile picture", Toast.LENGTH_SHORT).show();
                        profilePictureView.setVisibility(View.GONE);
                    });
                }
        );

        requestQueue.add(imageRequest);
    }



    private void makePostRequest(String extractedValue, String action) {
        OffsetDateTime now = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            now = OffsetDateTime.now(ZoneId.of("Asia/Manila"));
        }
        String currentPhTime = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            currentPhTime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        String encodedDateTime;
        try {
            encodedDateTime = URLEncoder.encode(currentPhTime, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e("URLEncoding", "Error encoding date-time", e);
            Toast.makeText(UserInfoActivity.this, "Error preparing request", Toast.LENGTH_SHORT).show();
            return;
        }

        String dateParam = action.equals("attend") ? "attendanceDate" : "timeoutDate";
        String url =  ipAddress+ "/api/v1/auth/" + action + "/" + eventId + "/" + extractedValue + "/?" + dateParam + "=" + encodedDateTime;

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.d("NetworkResponse", "Success: " + response);
                    String message = action.equals("attend") ?
                            "Attendance submitted successfully! " :
                            "Timeout recorded successfully!";
                    Toast.makeText(UserInfoActivity.this, message, Toast.LENGTH_LONG).show();

                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(action + "Success", true);
                    setResult(RESULT_OK, returnIntent);
                    finish();
                },
                error -> {
                    String errorMessage = "Network Error";
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        if (statusCode == 400) {
                            try {
                                String responseBody = new String(error.networkResponse.data, "UTF-8");
                                JSONObject jsonObject = new JSONObject(responseBody);
                                if (jsonObject.has("messages")) {
                                    errorMessage = jsonObject.getString("messages");
                                }
                            } catch (UnsupportedEncodingException | JSONException e) {
                                Log.e("ErrorParsing", "Error parsing error response", e);
                            }
                        } else {
                            errorMessage = "Cant Timeout Because You have not Checked for Attendance first";
                        }
                    } else if (error.getMessage() != null) {
                        errorMessage += ": " + error.getMessage();
                    }
                    Log.e("NetworkError", errorMessage);
                    Toast.makeText(UserInfoActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
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
}