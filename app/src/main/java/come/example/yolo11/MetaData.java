package come.example.yolo11;

import android.content.Context;

import org.tensorflow.lite.support.metadata.MetadataExtractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetaData {

    public static List<String> extractNamesFromMetadata(MappedByteBuffer model) {
        try {
            MetadataExtractor metadataExtractor = new MetadataExtractor(model);
            InputStream inputStream = metadataExtractor.getAssociatedFile("temp_meta.txt");

            if (inputStream == null) {
                return Collections.emptyList();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder metadataBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                metadataBuilder.append(line);
            }

            String metadata = metadataBuilder.toString();

            // Regex to extract 'names' content
            Pattern pattern = Pattern.compile("'names': \\{(.*?)\\}", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(metadata);
            if (!matcher.find()) {
                return Collections.emptyList();
            }

            String namesContent = matcher.group(1);

            // Regex to extract names from the content
            Pattern pattern2 = Pattern.compile("\"([^\"]*)\"|'([^']*)'");
            Matcher matcher2 = pattern2.matcher(namesContent);
            List<String> list = new ArrayList<>();
            while (matcher2.find()) {
                list.add(matcher2.group(1).isEmpty() ? matcher2.group(2) : matcher2.group(1));
            }

            return list;

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static List<String> extractNamesFromLabelFile(Context context, String labelPath) {
        List<String> labels = new ArrayList<>();
        try {
            InputStream inputStream = context.getAssets().open(labelPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                labels.add(line);
            }

            reader.close();
            inputStream.close();
            return labels;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static final List<String> TEMP_CLASSES = createTempClasses();

    private static List<String> createTempClasses() {
        List<String> tempClasses = new ArrayList<>(1000);
        for (int i = 1; i <= 1000; i++) {
            tempClasses.add("class" + i);
        }
        return tempClasses;
    }
}