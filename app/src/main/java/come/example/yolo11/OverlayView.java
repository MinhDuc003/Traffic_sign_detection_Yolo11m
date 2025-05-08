package come.example.yolo11;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private List<BoundingBox> results = new ArrayList<>();
    private Paint boxPaint = new Paint();
    private Paint textBackgroundPaint = new Paint();
    private Paint textPaint = new Paint();
    private Rect bounds = new Rect();

    private static final int BOUNDING_RECT_TEXT_PADDING = 8;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public void clear() {
        results.clear();
        textPaint.reset();
        textBackgroundPaint.reset();
        boxPaint.reset();
        invalidate();
        initPaints();
    }

    private void initPaints() {
        textBackgroundPaint.setColor(Color.WHITE);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setTextSize(36f);

        textPaint.setColor(Color.BLACK);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(36f);

        boxPaint.setColor(ContextCompat.getColor(getContext(), R.color.white));
        boxPaint.setStrokeWidth(6f);
        boxPaint.setStyle(Paint.Style.STROKE);
        
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (BoundingBox boundingBox : results) {
            float left = boundingBox.getX1() * getWidth();
            float top = boundingBox.getY1() * getHeight();
            float right = boundingBox.getX2() * getWidth();
            float bottom = boundingBox.getY2() * getHeight();

            canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, boxPaint);

            String drawableText = String.format("%s %.2f", boundingBox.getClsName(), boundingBox.getCnf());

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length(), bounds);
            int textWidth = bounds.width();
            int textHeight = bounds.height();

            RectF textBackgroundRect = new RectF(
                    left,
                    top,
                    left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                    top + textHeight + BOUNDING_RECT_TEXT_PADDING
            );
            canvas.drawRoundRect(textBackgroundRect, 8f, 8f, textBackgroundPaint);

            canvas.drawText(drawableText, left, top + textHeight, textPaint);
        }
    }

    public void setResults(List<BoundingBox> boundingBoxes) {
        results = boundingBoxes;
        invalidate();
    }
}
