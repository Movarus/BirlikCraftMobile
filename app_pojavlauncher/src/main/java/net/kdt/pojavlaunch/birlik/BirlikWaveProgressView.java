package net.kdt.pojavlaunch.birlik;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A rounded glass tube that fills with gold liquid. The front of the liquid ripples like a wave
 * while it fills; with an unknown progress (-1) a wave flows through the tube.
 */
public class BirlikWaveProgressView extends View {
    private static final int GOLD_LIGHT = 0xFFF2CB5A;
    private static final int GOLD = 0xFFD4A33B;
    private static final int GOLD_DEEP = 0xFF9A6A22;

    private final Paint mTubePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLiquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mTube = new Path();
    private final Path mLiquid = new Path();
    private final RectF mRect = new RectF();

    private float mProgress = -1f;      // 0..1, or -1 for indeterminate
    private float mShownProgress = 0f;  // eased value that is drawn
    private float mPhase;

    public BirlikWaveProgressView(Context context) { this(context, null); }

    public BirlikWaveProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTubePaint.setColor(0xCC0E0F12);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setColor(0x99D4A33B);
        mShinePaint.setColor(0x33FFFFFF);
    }

    /** @param percent 0-100, or a negative value when the progress is unknown */
    public void setProgress(int percent) {
        mProgress = percent < 0 ? -1f : Math.min(1f, percent / 100f);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float stroke = Math.max(2f, h * 0.06f);
        mBorderPaint.setStrokeWidth(stroke);
        mRect.set(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2);
        mTube.reset();
        mTube.addRoundRect(mRect, h / 2f, h / 2f, Path.Direction.CW);
        mLiquidPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{GOLD_LIGHT, GOLD, GOLD_DEEP}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        canvas.drawPath(mTube, mTubePaint);

        canvas.save();
        canvas.clipPath(mTube);
        float amplitude = h * 0.18f;
        mLiquid.reset();
        if (mProgress < 0) {
            // Indeterminate: a band of liquid flows through the tube
            float band = w * 0.35f;
            float start = ((mPhase * 0.25f) % 1f) * (w + band) - band;
            buildBand(start, start + band, amplitude, h);
        } else {
            mShownProgress += (mProgress - mShownProgress) * 0.12f;
            float front = mShownProgress * w;
            buildBand(-amplitude * 2, front, amplitude, h);
        }
        canvas.drawPath(mLiquid, mLiquidPaint);
        // Glass shine on the upper part of the tube
        canvas.drawRect(0, mRect.top, w, mRect.top + h * 0.22f, mShinePaint);
        canvas.restore();

        canvas.drawPath(mTube, mBorderPaint);

        mPhase += 0.06f;
        postInvalidateOnAnimation();
    }

    /** Liquid between two x positions whose front edge ripples vertically like a wave. */
    private void buildBand(float left, float right, float amplitude, int h) {
        mLiquid.moveTo(left, 0);
        mLiquid.lineTo(right, 0);
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            float y = h * i / (float) steps;
            float x = right + (float) Math.sin(mPhase * 2.2f + i * 0.9f) * amplitude;
            mLiquid.lineTo(x, y);
        }
        mLiquid.lineTo(left, h);
        mLiquid.close();
    }
}
