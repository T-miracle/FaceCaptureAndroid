package io.github.unifacecapture;

import android.content.Context;
import android.view.SurfaceView;

/** Center-crops the camera preview instead of stretching it to the screen. */
final class AspectRatioSurfaceView extends SurfaceView {
    private float aspectRatio;

    AspectRatioSurfaceView(Context context) {
        super(context);
    }

    void setAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        aspectRatio = width / (float) height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (aspectRatio <= 0f || availableWidth == 0 || availableHeight == 0) {
            setMeasuredDimension(availableWidth, availableHeight);
            return;
        }
        float containerRatio = availableWidth / (float) availableHeight;
        if (containerRatio > aspectRatio) {
            setMeasuredDimension(availableWidth, Math.round(availableWidth / aspectRatio));
        } else {
            setMeasuredDimension(Math.round(availableHeight * aspectRatio), availableHeight);
        }
    }
}
