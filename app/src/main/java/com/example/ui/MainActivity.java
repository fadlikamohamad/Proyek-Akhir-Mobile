package com.example.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.example.ui.R;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

public class MainActivity extends AppCompatActivity {

    final int SELECT_IMAGES = 1;
    ArrayList<Uri> selectedImagesPaths;
    boolean imageSelected = false;
    ImageView dermoscopyImage;
    TextView fileName;
    TextView classificationResult;
    private ProgressBar loading;
    Button predictButton, resetButton;
    PieChart pieChart;
    PieData pieData;
    List<PieEntry> pieEntryList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fileName = findViewById(R.id.fileName);
        dermoscopyImage = findViewById(R.id.dermoscopyImage);
        predictButton = findViewById(R.id.predict);
        resetButton = findViewById(R.id.reset);
        predictButton.setEnabled(false);
        resetButton.setEnabled(false);
        classificationResult = findViewById(R.id.classificationResult);
        loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        pieChart = findViewById(R.id.pieChart);
        pieChart.setNoDataText("");
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
                if (data.getData() != null){
                    Uri uri = data.getData();
                    selectedImagesPaths.add(uri);
                    imageSelected = true;
                    fileName.setText(getFileName(selectedImagesPaths.get(0)));
                    dermoscopyImage.setImageURI(selectedImagesPaths.get(0));
                    predictButton.setEnabled(true);
                    resetButton.setEnabled(true);
                } else {
                    Toast.makeText(this, "Anda belum memilih gambar apapun.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e){
            Toast.makeText(this, "Ada yang salah.", Toast.LENGTH_SHORT).show();
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
        if (imageSelected == false){
            classificationResult.setText("Tidak ada gambar yang dipilih untuk diunggah.");
            return;
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());

        String postURL = "http://192.168.88.142:5000/prediction/";
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
                classificationResult.setText("Pastikan file yang dipilih adalah gambar!");
                return;
            }
            multiBuilder.addFormDataPart("file", getFileName(selectedImagesPaths.get(0)), RequestBody.create(MediaType.parse("image/*jpg"), byteArray));
        }
        RequestBody postBodyImage = multiBuilder.build();
        postRequest(postURL, postBodyImage);
    }

    public void postRequest(String postURL, RequestBody postBody){
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(postURL).post(postBody).build();
        loading.setVisibility(View.VISIBLE);
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
                Log.d("FAIL", e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView classificationResult = findViewById(R.id.classificationResult);
                        classificationResult.setText("Gagal terhubung ke server. Silahkan coba lagi!");
                        loading.setVisibility(View.GONE);
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

                                JSONObject class_score_1 = jsonArray.getJSONObject(0);
                                String class1 = class_score_1.getString("class");
                                double score1 = class_score_1.getDouble("score");
                                JSONObject class_score_2 = jsonArray.getJSONObject(1);
                                String class2 = class_score_2.getString("class");
                                double score2 = class_score_2.getDouble("score");

                                pieChart.setUsePercentValues(true);
                                pieEntryList.add(new PieEntry((float) score1, class1));
                                pieEntryList.add(new PieEntry((float) score2, class2));

                                PieDataSet pieDataSet = new PieDataSet(pieEntryList, "");
                                pieDataSet.setColors(ColorTemplate.MATERIAL_COLORS);
                                pieData = new PieData(pieDataSet);
                                pieChart.setData(pieData);
                                pieChart.setDrawHoleEnabled(false);
                                pieChart.invalidate();
                                pieChart.getDescription().setEnabled(false);
                                pieChart.setDrawSliceText(false);
                                Legend legend = pieChart.getLegend();
                                legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
                            }
                            else
                                classificationResult.setText("Oops! Ada yang salah.\nSilahkan coba lagi!");
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                        loading.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    public void reset(View view) {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
        overridePendingTransition(0,0);
    }
}