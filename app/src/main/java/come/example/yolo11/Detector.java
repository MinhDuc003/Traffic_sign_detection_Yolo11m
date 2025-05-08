package come.example.yolo11;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Detector {


    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.5F;

    private Context context;
    private String modelPath;
    private String labelPath;
    private DetectorListener detectorListener;
    private MessageCallback message;

    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();

    private int tensorWidth = 0;
    private int tensorHeight = 0;
    private int numChannel = 0;
    private int numElements = 0;

    private final ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(new CastOp(INPUT_IMAGE_TYPE))
            .build();

    public Detector(Context context, String modelPath, String labelPath, DetectorListener detectorListener, MessageCallback message) throws IOException {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = detectorListener;
        this.message = message;

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);

        MappedByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
        interpreter = new Interpreter(model, options);

        labels.addAll(MetaData.extractNamesFromMetadata(model));
        if (labels.isEmpty()) {
            if (labelPath == null) {
                message.onMessage("Model does not contain metadata, provide LABELS_PATH in Constants.kt");
                labels.addAll(MetaData.TEMP_CLASSES);
            } else {
                labels.addAll(MetaData.extractNamesFromLabelFile(context, labelPath));
            }
        }

        for (String label : labels) {
            System.out.println(label);
        }

        int[] inputShape = interpreter.getInputTensor(0).shape();
        int[] outputShape = interpreter.getOutputTensor(0).shape();

        if (inputShape != null) {
            tensorWidth = inputShape[1];
            tensorHeight = inputShape[2];

            // If in case input shape is in format of [1, 3, ..., ...]
            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2];
                tensorHeight = inputShape[3];
            }
        }

        if (outputShape != null) {
            numElements = outputShape[1];
            numChannel = outputShape[2];
        }
    }

    public void restart(boolean isGpu) throws IOException {
        interpreter.close();

        Interpreter.Options options;
        if (isGpu) {
            CompatibilityList compatList = new CompatibilityList();
            options = new Interpreter.Options();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
                options.addDelegate(new GpuDelegate(delegateOptions));
            } else {
                options.setNumThreads(4);
            }
        } else {
            options = new Interpreter.Options();
            options.setNumThreads(4);
        }

        MappedByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
        interpreter = new Interpreter(model, options);
    }

    public void close() {
        interpreter.close();
    }

    public void detect(Bitmap frame) {
        if (tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) return;

        long inferenceTime = SystemClock.uptimeMillis();

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);

        TensorImage tensorImage = new TensorImage(INPUT_IMAGE_TYPE);
        tensorImage.load(resizedBitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        TensorBuffer imageBuffer = processedImage.getTensorBuffer();

        TensorBuffer output = TensorBuffer.createFixedSize(new int[]{1, numChannel, numElements}, OUTPUT_IMAGE_TYPE);
        interpreter.run(imageBuffer.getBuffer(), output.getBuffer());

        List<BoundingBox> bestBoxes = bestBox(output.getFloatArray());
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;

        if (bestBoxes.isEmpty()) {
            detectorListener.onEmptyDetect();
            return;
        }

        detectorListener.onDetect(bestBoxes, inferenceTime);
    }

    private List<BoundingBox> bestBox(float[] array) {
        List<BoundingBox> boundingBoxes = new ArrayList<>();
        for (int r = 0; r < numElements; r++) {
            float cnf = array[r * numChannel + 4];
            if (cnf > CONFIDENCE_THRESHOLD) {
                float x1 = array[r * numChannel];
                float y1 = array[r * numChannel + 1];
                float x2 = array[r * numChannel + 2];
                float y2 = array[r * numChannel + 3];
                int cls = (int) array[r * numChannel + 5];
                String clsName = labels.get(cls);
                boundingBoxes.add(new BoundingBox(x1, y1, x2, y2, cnf, cls, clsName));
            }
        }
        return boundingBoxes;
    }

    public interface DetectorListener {
        void onEmptyDetect();
        void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime);
    }

    public interface MessageCallback {
        void onMessage(String message);
    }

}