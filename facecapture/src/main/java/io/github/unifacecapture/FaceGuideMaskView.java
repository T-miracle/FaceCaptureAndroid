package io.github.unifacecapture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws the dimmed area around the centered square guide. */
final class FaceGuideMaskView extends View {
    static final float FRAME_WIDTH_FRACTION = 0.75f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();

    FaceGuideMaskView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    RectF getFrameRect() {
        updateFrameRect();
        return new RectF(frameRect);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        updateFrameRect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateFrameRect();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        canvas.drawRect(0, 0, getWidth(), frameRect.top, paint);
        canvas.drawRect(0, frameRect.bottom, getWidth(), getHeight(), paint);
        canvas.drawRect(0, frameRect.top, frameRect.left, frameRect.bottom, paint);
        canvas.drawRect(frameRect.right, frameRect.top, getWidth(), frameRect.bottom, paint);
    }

    private void updateFrameRect() {
        float side = getWidth() * FRAME_WIDTH_FRACTION;
        float left = (getWidth() - side) / 2f;
        float top = (getHeight() - side) / 2f;
        frameRect.set(left, top, left + side, top + side);
    }
}
