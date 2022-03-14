package com.example.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    final int SELECT_IMAGES = 1;
    ArrayList<Uri> selectedImagesPaths;
    boolean imageSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void chooseFile(View v){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Dermoscopy Image"), SELECT_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == SELECT_IMAGES && resultCode == RESULT_OK && data != null){
                selectedImagesPaths = new ArrayList<>();
                TextView fileName = findViewById(R.id.fileName);
                ImageView dermoscopyImage = findViewById(R.id.dermoscopyImage);
                if (data.getData() != null){
                    Uri uri = data.getData();
                    selectedImagesPaths.add(uri);
                    imageSelected = true;
                    fileName.setText(getFileName(selectedImagesPaths.get(0)));
                    dermoscopyImage.setImageURI(selectedImagesPaths.get(0));
                } else {
                    Toast.makeText(this, "You haven't picked any image.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e){
            Toast.makeText(this, "Something went wrong.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public String getFileName(Uri uri){
        String result = null;
        if (uri.getScheme().equals("content")){
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try{
                if (cursor != null && cursor.moveToFirst()){
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null){
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1){
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void predict(View v) {
        TextView classificationResult = findViewById(R.id.classificationResult);
        if (imageSelected == false){
            classificationResult.setText("No Image Selected to Upload.");
            return;
        }

        String postURL = "http://192.168.165.142:5000/prediction/";
        MultipartBody.Builder multiBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        for (int i = 0; i < selectedImagesPaths.size(); i++){
            byte[] byteArray = null;
            try{
                InputStream inputStream = getContentResolver().openInputStream(selectedImagesPaths.get(i));
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                int len = 0;
                while ((len = inputStream.read(buffer)) != -1){
                    byteBuffer.write(buffer, 0, len);
                }
                byteArray = byteBuffer.toByteArray();
            } catch (Exception e){
                classificationResult.setText("Please Make Sure the Selected File is an Image.");
                return;
            }
            multiBuilder.addFormDataPart("file", "input_image.jpg", RequestBody.create(MediaType.parse("image/*jpg"), byteArray));
        }
        RequestBody postBodyImage = multiBuilder.build();
        postRequest(postURL, postBodyImage);
    }

    public void postRequest(String postURL, RequestBody postBody){
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(postURL).post(postBody).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
                Log.d("FAIL", e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView classificationResult = findViewById(R.id.classificationResult);
                        classificationResult.setText("Failed to Connect to Server. Please Try Again.");

                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void run() {

                        TextView classificationResult = findViewById(R.id.classificationResult);
                        try {
                            String[] res = response.toString().split(",");
                            if (res[1].trim().equals("code=200")) {
                                String result = response.body().string();
                                JSONObject jsonObject = new JSONObject(result);
                                JSONArray jsonArray = jsonObject.getJSONArray("result");
                                JSONObject object = jsonArray.getJSONObject(2);
                                String predicted_label = object.getString("predicted_label");
                                classificationResult.setText(predicted_label);
                            }
                            else
                                classificationResult.setText("Oops! Something went wrong.\nPlease try again.");
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }
}