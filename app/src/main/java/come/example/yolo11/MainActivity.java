package come.example.yolo11;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import come.example.yolo11.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements Detector.DetectorListener {

    private ActivityMainBinding binding;
    private Activity activity;
    private Context context;
    private boolean isFrontCamera = false;
    private Preview preview;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private Detector detector;
    private ExecutorService cameraExecutor;
    private RecyclerView recyclerView;
    private MyAdapter adapter;
    private List<String> itemList;
    private TextToSpeech textToSpeech;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        binding = ActivityMainBinding.inflate(getLayoutInflater());

        activity = MainActivity.this;
        context = this;
        setContentView(binding.getRoot());
        //tạo Bottomsheet
        View bottomSheet = findViewById(R.id.sheet);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setPeekHeight(50);
        behavior.setHideable(false);
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Khởi tạo RecyclerView
        recyclerView = findViewById(R.id.recyclerView);


        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("vi", "VN"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(context, "Ngôn ngữ không được hỗ trợ hoặc thiếu dữ liệu", Toast.LENGTH_SHORT).show();
                    Intent installIntent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                }
                // Tạo danh sách item
                itemList = new ArrayList<>();


                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                adapter = new MyAdapter(itemList, textToSpeech);
                recyclerView.setAdapter(adapter);

            } else {
                Toast.makeText(context, "Khởi tạo TextToSpeech thất bại", Toast.LENGTH_SHORT).show();
            }
        });


        // Gán Adapter cho RecyclerView
        adapter = new MyAdapter(itemList,textToSpeech);
        recyclerView.setAdapter(adapter);


        cameraExecutor = Executors.newSingleThreadExecutor();

        cameraExecutor.execute(() -> {
            try {
                detector = new Detector(
                        this,
                        Constants.MODEL_PATH,
                        Constants.LABELS_PATH,
                        this,
                        this::toast
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions( this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
            );
        }
        bindListeners();
    }
    private void sendDetectedSigns(List<String> labels) {
        activity.runOnUiThread(() -> updateRecyclerView(labels));
    }

    private void updateRecyclerView(List<String> labels) {
        if (adapter == null || itemList == null) return;

        Set<String> uniqueLabels = new HashSet<>(labels);
        itemList.clear();
        itemList.addAll(uniqueLabels);
        adapter.clearExplanations();
        adapter.notifyDataSetChanged();
    }



    private static final String CAMERA = "android.permission.CAMERA";
    private static final int REQUEST_CAMERA = 0;

    private static final String TAG = "Camera";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private void RequestPermissions() {
        int hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA);
        if (hasCameraPermission != PermissionChecker.PERMISSION_GRANTED ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA)) {
                Toast.makeText(activity, "Camera Permission", Toast.LENGTH_SHORT).show();
            }else {
                startCamera();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            int hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA);
            if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
                Toast.makeText(activity, "Camera Permission required!", Toast.LENGTH_SHORT).show();
                return;
            }

            RequestPermissions();
        }
    }

    private void bindListeners() {
        binding.cbGPU.setOnCheckedChangeListener((buttonView, isChecked) ->
                cameraExecutor.submit(() -> {
                    try {
                        detector.restart(isChecked);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
        );
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(context).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            throw new IllegalStateException("Camera initialization failed.");
        }

        int rotation = binding.viewFinder.getDisplay().getRotation();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(binding.viewFinder.getDisplay().getRotation())
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
            Bitmap bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            try {
                // Obtain the buffer from the imageProxy's plane and copy the pixels to the bitmapBuffer
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            } finally {
                // Ensure that the imageProxy is closed after use
                imageProxy.close();
            }

//            imageProxy.use(() -> bitmapBuffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer()));
            imageProxy.close();


            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());

            if (isFrontCamera) {
                matrix.postScale(
                        -1f,
                        1f,
                        imageProxy.getWidth() / 2f,
                        imageProxy.getHeight() / 2f
                );
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer,
                    0,
                    0,
                    bitmapBuffer.getWidth(),
                    bitmapBuffer.getHeight(),
                    matrix,
                    true
            );

            detector.detect(rotatedBitmap);
        });

        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(
                    (LifecycleOwner) this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
            );

            preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(android.Manifest.permission.CAMERA))) {
                    startCamera();
                }
            }
    );
    @Override
    public void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    @Override
    public void onEmptyDetect() {
        activity.runOnUiThread(() -> binding.overlay.clear());
    }

    @Override
    public void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime) {
        for (BoundingBox box : boundingBoxes) {
            Log.d("DETECT", "Detected: " + box.getClsName());
        }

        // Gợi ý gửi tên biển báo về server
        List<String> labels = new ArrayList<>();
        for (BoundingBox box : boundingBoxes) {
            labels.add(box.getClsName());
        }
        sendDetectedSigns(labels);


        activity.runOnUiThread(() -> {
            binding.inferenceTime.setText(inferenceTime + "ms");
            binding.overlay.setResults(boundingBoxes);
            binding.overlay.invalidate();
        });
    }
    private void toast(String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    public static class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {

        private List<String> itemList;
        private final TextToSpeech textToSpeech;
        private Map<String, String> explanationMap = new HashMap<>();

        public void updateExplanations(Map<String, String> explanations) {
            this.explanationMap.clear();
            this.explanationMap.putAll(explanations);
            notifyDataSetChanged(); // Thông báo cho Adapter cập nhật
        }


        public MyAdapter(List<String> itemList, TextToSpeech tts) {
            this.itemList = itemList;
            this.textToSpeech = tts;
            this.explanationMap = new HashMap<>();
        }


        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String item = itemList.get(position);
            holder.itemText.setText(item);

            holder.button1.setOnClickListener(v -> {
                if (holder.textViewExplanation.getVisibility() == View.VISIBLE) {
                    collapseView(holder.textViewExplanation);
                } else {
                    expandView(holder.textViewExplanation);
                }
                fetchExplanation(item, holder.textViewExplanation, position);
            });

            // Set listener cho Button 2(speak)
            holder.button2.setOnClickListener(v -> {
                String toSpeak = holder.textViewExplanation.getText().toString();
                int status = textToSpeech.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                if (status == TextToSpeech.ERROR) {
                    Toast.makeText(holder.itemView.getContext(), "Không thể phát âm", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Mở rộng view với animation
        private void expandView(View view) {
            view.setVisibility(View.VISIBLE);
            view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            final int targetHeight = view.getMeasuredHeight();

            view.getLayoutParams().height = 0;
            view.requestLayout();

            Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    view.getLayoutParams().height = interpolatedTime == 1
                            ? ViewGroup.LayoutParams.WRAP_CONTENT
                            : (int) (targetHeight * interpolatedTime);
                    view.requestLayout();
                }

                @Override
                public boolean willChangeBounds() {
                    return true;
                }
            };

            animation.setDuration((int) (targetHeight / view.getContext().getResources().getDisplayMetrics().density));
            view.startAnimation(animation);
        }

        // Thu gọn view với animation
        private void collapseView(View view) {
            final int initialHeight = view.getMeasuredHeight();

            Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    if (interpolatedTime == 1) {
                        view.setVisibility(View.GONE);
                    } else {
                        view.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                        view.requestLayout();
                    }
                }

                @Override
                public boolean willChangeBounds() {
                    return true;
                }
            };

            animation.setDuration((int) (initialHeight / view.getContext().getResources().getDisplayMetrics().density));
            view.startAnimation(animation);
        }

        private void fetchExplanation(String label, TextView explanationView, int position) {
            String url = "http://172.16.12.57:5000/explain_sign";

            RequestQueue queue = Volley.newRequestQueue(explanationView.getContext());

            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("label", label);
            } catch (Exception e) {
                Log.e("JSON_ERROR", "Error creating JSON body", e);
                return;
            }

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                    response -> {
                        Log.d("API Response", "Response: " + response.toString());
                        String explanation = response.optString("explanation", "Không có giải thích.");
                        explanationMap.put(label, explanation);
                        expandView(explanationView);
                        explanationView.setText(explanation);
                    },
                    error -> {
                        explanationView.setText("Không thể tải giải thích.");
                        expandView(explanationView);
                        Log.e("EXPLAIN", "Error fetching explanation: " + error.toString());
                    }
            );
            request.setRetryPolicy(new DefaultRetryPolicy(
                    10000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            ));

            queue.add(request);
        }



        public void clearExplanations() {
            explanationMap.clear();
        }



        @Override
        public int getItemCount() {
            return itemList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {

            TextView itemText;
            TextView textViewExplanation;

            Button button1, button2;

            public ViewHolder(View itemView) {
                super(itemView);
                itemText = itemView.findViewById(R.id.textViewItem);
                button1 = itemView.findViewById(R.id.buttonLeft);
                button2 = itemView.findViewById(R.id.buttonRight);
                textViewExplanation = itemView.findViewById(R.id.textViewExplanation);
            }
        }
    }
}