package io.github.unifacecapture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Full-screen portrait UI with pinch zoom and switchable rear/front cameras. */
public final class FaceCaptureActivity extends Activity implements SurfaceHolder.Callback {
    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_ERROR_CODE = "resultErrorCode";
    public static final String EXTRA_RESULT_MESSAGE = "resultMessage";
    public static final String EXTRA_RESULT_PATH = "resultPath";
    public static final String EXTRA_RESULT_WIDTH = "resultWidth";
    public static final String EXTRA_RESULT_HEIGHT = "resultHeight";

    private static final int CAMERA_PERMISSION_REQUEST = 2501;
    private static final int MAX_DECODE_DIMENSION = 2400;

    private FrameLayout root;
    private AspectRatioSurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private FaceGuideMaskView maskView;
    private FrameLayout guideLayer;
    private View shutterButton;
    private ImageView switchButton;
    private TextView zoomHint;
    private ScaleGestureDetector scaleGestureDetector;
    private Camera camera;
    private int cameraId = -1;
    private int cameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int jpegRotation;
    private String sessionId;
    private boolean surfaceReady;
    private boolean previewStarted;
    private boolean captureInProgress;
    private boolean resolved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureSystemBars();
        buildContentView();
        ensureCameraPermission();
    }

    /** Hides the status bar while leaving the system navigation area available. */
    private void configureSystemBars() {
        Window window = getWindow();
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void buildContentView() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new AspectRatioSurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        scaleGestureDetector = new ScaleGestureDetector(this, new ZoomGestureListener());
        surfaceView.setOnTouchListener((view, event) -> scaleGestureDetector.onTouchEvent(event));
        root.addView(surfaceView, matchParentParams(Gravity.CENTER));

        maskView = new FaceGuideMaskView(this);
        root.addView(maskView, matchParentParams(Gravity.CENTER));

        guideLayer = createGuideLayer();
        root.addView(guideLayer, new FrameLayout.LayoutParams(1, 1));
        root.addView(createBackButton(), backButtonParams());
        shutterButton = createShutterButton();
        root.addView(shutterButton, new FrameLayout.LayoutParams(dp(76), dp(76)));
        zoomHint = createZoomHint();
        root.addView(zoomHint, new FrameLayout.LayoutParams(dp(220), dp(24)));
        switchButton = createSwitchButton();
        root.addView(switchButton, new FrameLayout.LayoutParams(dp(52), dp(52)));
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> positionGuideAndControls());

        setContentView(root);
    }

    private FrameLayout createGuideLayer() {
        FrameLayout layer = new FrameLayout(this);
        ImageView guide = new ImageView(this);
        guide.setImageResource(R.drawable.face_guide_overlay);
        guide.setScaleType(ImageView.ScaleType.FIT_XY);
        layer.addView(guide, matchParentParams(Gravity.CENTER));

        View border = new View(this);
        border.setBackground(roundDrawable(Color.TRANSPARENT, Color.WHITE, dp(2), 0));
        layer.addView(border, matchParentParams(Gravity.CENTER));
        return layer;
    }

    private View createBackButton() {
        TextView button = new TextView(this);
        button.setText("‹");
        button.setTextColor(Color.WHITE);
        button.setTextSize(42);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> cancel());
        return button;
    }

    private View createShutterButton() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackground(roundDrawable(0x33FFFFFF, Color.WHITE, dp(3), dp(38)));
        View inner = new View(this);
        inner.setBackground(roundDrawable(Color.WHITE, Color.WHITE, 0, dp(30)));
        FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER);
        outer.addView(inner, innerParams);
        outer.setOnClickListener(view -> takePhoto());
        return outer;
    }

    private TextView createZoomHint() {
        TextView hint = new TextView(this);
        hint.setText("双指缩放可调整焦距");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        return hint;
    }

    private ImageView createSwitchButton() {
        ImageView button = new ImageView(this);
        button.setImageResource(R.drawable.camera_switch);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setContentDescription("切换前后摄像头");
        button.setBackground(roundDrawable(0x33000000, 0x66FFFFFF, dp(1), dp(26)));
        button.setOnClickListener(view -> switchCamera());
        return button;
    }

    private FrameLayout.LayoutParams matchParentParams(int gravity) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        params.gravity = gravity;
        return params;
    }

    private FrameLayout.LayoutParams backButtonParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.START);
        params.setMarginStart(dp(12));
        params.topMargin = dp(12);
        return params;
    }

    private void positionGuideAndControls() {
        RectF frame = maskView.getFrameRect();
        if (frame.isEmpty()) {
            return;
        }
        FrameLayout.LayoutParams guideParams = (FrameLayout.LayoutParams) guideLayer.getLayoutParams();
        guideParams.width = Math.round(frame.width());
        guideParams.height = Math.round(frame.height());
        guideParams.setMarginStart(Math.round(frame.left));
        guideParams.topMargin = Math.round(frame.top);
        guideParams.gravity = Gravity.TOP | Gravity.START;
        guideLayer.setLayoutParams(guideParams);

        FrameLayout.LayoutParams hintParams = (FrameLayout.LayoutParams) zoomHint.getLayoutParams();
        hintParams.setMarginStart(Math.round(frame.centerX() - dp(110)));
        hintParams.topMargin = Math.round(frame.bottom + dp(16));
        hintParams.gravity = Gravity.TOP | Gravity.START;
        zoomHint.setLayoutParams(hintParams);

        int bottomClearance = Math.max(dp(12), root.getHeight() / 8);
        int shutterTop = Math.max(0, root.getHeight() - bottomClearance - dp(76));
        FrameLayout.LayoutParams shutterParams = (FrameLayout.LayoutParams) shutterButton.getLayoutParams();
        shutterParams.setMarginStart(Math.round(frame.centerX() - dp(38)));
        shutterParams.topMargin = shutterTop;
        shutterParams.gravity = Gravity.TOP | Gravity.START;
        shutterButton.setLayoutParams(shutterParams);

        FrameLayout.LayoutParams switchParams = (FrameLayout.LayoutParams) switchButton.getLayoutParams();
        switchParams.setMarginStart(Math.round(frame.centerX() + dp(64)));
        switchParams.topMargin = shutterTop + dp(12);
        switchParams.gravity = Gravity.TOP | Gravity.START;
        switchButton.setLayoutParams(switchParams);
    }

    private GradientDrawable roundDrawable(
        int fillColor,
        int strokeColor,
        int strokeWidth,
        int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void ensureCameraPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCameraIfReady();
            return;
        }
        requestPermissions(new String[]{ Manifest.permission.CAMERA }, CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST
            && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCameraIfReady();
            return;
        }
        fail("CAMERA_PERMISSION_DENIED", "未获得相机权限");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        openCameraIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null && previewStarted) {
            restartPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
    }

    private void openCameraIfReady() {
        if (camera != null || !surfaceReady || surfaceHolder == null
            || !surfaceHolder.getSurface().isValid() || !hasCameraPermission()) {
            return;
        }
        cameraId = findCamera(cameraFacing);
        if (cameraId < 0) {
            cameraFacing = oppositeFacing(cameraFacing);
            cameraId = findCamera(cameraFacing);
        }
        if (cameraId < 0) {
            fail("CAMERA_UNAVAILABLE", "设备没有可用的摄像头");
            return;
        }
        try {
            camera = Camera.open(cameraId);
            updatePreviewMirroring();
            configureCamera();
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            previewStarted = true;
            updateControlState();
        } catch (IOException | RuntimeException exception) {
            releaseCamera();
            fail("CAMERA_OPEN_FAILED", "相机启动失败，请重新进入页面");
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private int findCamera(int facing) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int index = 0; index < Camera.getNumberOfCameras(); index++) {
            Camera.getCameraInfo(index, info);
            if (info.facing == facing) {
                return index;
            }
        }
        return -1;
    }

    private void configureCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = displayDegrees();
        int displayOrientation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (360 - ((info.orientation + degrees) % 360)) % 360;
            jpegRotation = (info.orientation - degrees + 360) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
            jpegRotation = (info.orientation + degrees) % 360;
        }
        camera.setDisplayOrientation(displayOrientation);

        Camera.Parameters parameters = camera.getParameters();
        Camera.Size preview = choosePreviewSize(parameters.getSupportedPreviewSizes());
        if (preview != null) {
            parameters.setPreviewSize(preview.width, preview.height);
            int displayedWidth = displayOrientation % 180 == 0 ? preview.width : preview.height;
            int displayedHeight = displayOrientation % 180 == 0 ? preview.height : preview.width;
            surfaceView.setAspectRatio(displayedWidth, displayedHeight);
        }
        Camera.Size picture = choosePictureSize(parameters.getSupportedPictureSizes());
        if (picture != null) {
            parameters.setPictureSize(picture.width, picture.height);
        }
        parameters.setRotation(jpegRotation);
        parameters.setJpegQuality(95);
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        camera.setParameters(parameters);
    }

    /** Switches between rear and front cameras while keeping the capture UI in place. */
    private void switchCamera() {
        if (captureInProgress || camera == null) {
            return;
        }
        int targetFacing = oppositeFacing(cameraFacing);
        if (findCamera(targetFacing) < 0) {
            Toast.makeText(this, "设备没有可切换的摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        cameraFacing = targetFacing;
        switchButton.setEnabled(false);
        releaseCamera();
        openCameraIfReady();
        switchButton.setEnabled(camera != null);
        switchButton.setAlpha(camera == null ? 0.5f : 1f);
    }

    private int oppositeFacing(int facing) {
        return facing == Camera.CameraInfo.CAMERA_FACING_BACK
            ? Camera.CameraInfo.CAMERA_FACING_FRONT
            : Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    private void updatePreviewMirroring() {
        surfaceView.setScaleX(cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? -1f : 1f);
    }

    /** Applies the closest supported hardware zoom step for a pinch gesture. */
    private void applyZoom(float scaleFactor) {
        if (camera == null || captureInProgress) {
            return;
        }
        try {
            Camera.Parameters parameters = camera.getParameters();
            if (!parameters.isZoomSupported() || parameters.getMaxZoom() <= 0) {
                return;
            }
            int current = parameters.getZoom();
            int delta = Math.round((scaleFactor - 1f) * Math.max(4, parameters.getMaxZoom() / 4f));
            int target = Math.max(0, Math.min(parameters.getMaxZoom(), current + delta));
            if (target != current) {
                parameters.setZoom(target);
                camera.setParameters(parameters);
            }
        } catch (RuntimeException ignored) {
            // Some legacy camera drivers reject zoom changes while refocusing.
        }
    }

    private final class ZoomGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            applyZoom(detector.getScaleFactor());
            return true;
        }
    }

    private int displayDegrees() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_90) {
            return 90;
        }
        if (rotation == Surface.ROTATION_180) {
            return 180;
        }
        if (rotation == Surface.ROTATION_270) {
            return 270;
        }
        return 0;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        float targetRatio = getResources().getDisplayMetrics().widthPixels
            / (float) getResources().getDisplayMetrics().heightPixels;
        Camera.Size best = sizes.get(0);
        float bestDifference = Float.MAX_VALUE;
        for (Camera.Size size : sizes) {
            float displayedRatio = Math.min(size.width, size.height) / (float) Math.max(size.width, size.height);
            float difference = Math.abs(displayedRatio - targetRatio);
            if (difference < bestDifference
                || (difference == bestDifference && size.width * size.height > best.width * best.height)) {
                best = size;
                bestDifference = difference;
            }
        }
        return best;
    }

    private Camera.Size choosePictureSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = sizes.get(0);
        for (Camera.Size size : sizes) {
            if (size.width * size.height > best.width * best.height) {
                best = size;
            }
        }
        return best;
    }

    private void takePhoto() {
        if (captureInProgress) {
            return;
        }
        if (camera == null || !previewStarted) {
            Toast.makeText(this, "相机正在准备，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        captureInProgress = true;
        shutterButton.setEnabled(false);
        shutterButton.setAlpha(0.5f);
        switchButton.setEnabled(false);
        switchButton.setAlpha(0.5f);
        try {
            previewStarted = false;
            camera.takePicture(null, null, (data, ignoredCamera) -> processCapturedJpeg(data));
        } catch (RuntimeException exception) {
            captureInProgress = false;
            updateControlState();
            Toast.makeText(this, "拍照失败，请重试", Toast.LENGTH_SHORT).show();
            restartPreview();
        }
    }

    private void processCapturedJpeg(byte[] jpegData) {
        Bitmap source = null;
        Bitmap oriented = null;
        Bitmap cropped = null;
        try {
            source = decodeCapture(jpegData);
            oriented = orientPortrait(source);
            cropped = cropToGuideFrame(oriented);
            String path = saveCapture(cropped);
            complete(path, cropped.getWidth(), cropped.getHeight());
        } catch (IOException | RuntimeException exception) {
            Toast.makeText(this, "图片处理失败，请重拍", Toast.LENGTH_SHORT).show();
            captureInProgress = false;
            updateControlState();
            restartPreview();
        } finally {
            if (cropped != null && cropped != oriented && !cropped.isRecycled()) {
                cropped.recycle();
            }
            if (oriented != null && oriented != source && !oriented.isRecycled()) {
                oriented.recycle();
            }
            if (source != null && !source.isRecycled()) {
                source.recycle();
            }
        }
    }

    private Bitmap decodeCapture(byte[] jpegData) {
        if (jpegData == null || jpegData.length == 0) {
            throw new IllegalStateException("empty JPEG");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_DIMENSION);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, options);
        if (bitmap == null) {
            throw new IllegalStateException("JPEG decode failed");
        }
        return bitmap;
    }

    private int sampleSize(int width, int height, int maxDimension) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /** Some camera drivers rotate pixels for setRotation while others only write metadata. */
    private Bitmap orientPortrait(Bitmap source) {
        if (source.getHeight() >= source.getWidth() || jpegRotation == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(jpegRotation);
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.getWidth(),
            source.getHeight(),
            matrix,
            true
        );
    }

    /** Maps the visible square guide through the center-cropped preview into photo pixels. */
    private Bitmap cropToGuideFrame(Bitmap source) {
        RectF guide = maskView == null ? null : maskView.getFrameRect();
        if (guide == null || guide.isEmpty() || surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0) {
            return centeredSquare(source);
        }

        float previewLeft = surfaceView.getLeft();
        float previewTop = surfaceView.getTop();
        float previewWidth = surfaceView.getWidth();
        float previewHeight = surfaceView.getHeight();
        float normalizedLeft = clamp((guide.left - previewLeft) / previewWidth);
        float normalizedTop = clamp((guide.top - previewTop) / previewHeight);
        float normalizedRight = clamp((guide.right - previewLeft) / previewWidth);
        float normalizedBottom = clamp((guide.bottom - previewTop) / previewHeight);
        if (normalizedRight <= normalizedLeft || normalizedBottom <= normalizedTop) {
            return centeredSquare(source);
        }

        float sourceWidth = source.getWidth();
        float sourceHeight = source.getHeight();
        float sourceRatio = sourceWidth / sourceHeight;
        float previewRatio = previewWidth / previewHeight;
        float visibleLeft = 0f;
        float visibleTop = 0f;
        float visibleWidth = sourceWidth;
        float visibleHeight = sourceHeight;
        if (sourceRatio > previewRatio) {
            visibleWidth = sourceHeight * previewRatio;
            visibleLeft = (sourceWidth - visibleWidth) / 2f;
        } else if (sourceRatio < previewRatio) {
            visibleHeight = sourceWidth / previewRatio;
            visibleTop = (sourceHeight - visibleHeight) / 2f;
        }

        int left = Math.max(0, Math.round(visibleLeft + normalizedLeft * visibleWidth));
        int top = Math.max(0, Math.round(visibleTop + normalizedTop * visibleHeight));
        int right = Math.min(source.getWidth(), Math.round(visibleLeft + normalizedRight * visibleWidth));
        int bottom = Math.min(source.getHeight(), Math.round(visibleTop + normalizedBottom * visibleHeight));
        int side = Math.min(right - left, bottom - top);
        if (side <= 0) {
            return centeredSquare(source);
        }
        left += ((right - left) - side) / 2;
        top += ((bottom - top) - side) / 2;
        return Bitmap.createBitmap(source, left, top, side, side);
    }

    private Bitmap centeredSquare(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        return Bitmap.createBitmap(
            source,
            (source.getWidth() - side) / 2,
            (source.getHeight() - side) / 2,
            side,
            side
        );
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private String saveCapture(Bitmap bitmap) throws IOException {
        File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File outputDirectory = pictures == null ? getFilesDir() : pictures;
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("app storage unavailable");
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.CHINA).format(new Date());
        File output = new File(outputDirectory, "face_capture_" + timestamp + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                throw new IOException("JPEG write failed");
            }
        }
        return output.getAbsolutePath();
    }

    private void complete(String path, int width, int height) {
        JSONObject result = new JSONObject();
        result.put("code", 0);
        result.put("path", path);
        result.put("uri", Uri.fromFile(new File(path)).toString());
        result.put("width", width);
        result.put("height", height);
        finishWithResult(RESULT_OK, result);
    }

    private void cancel() {
        JSONObject result = new JSONObject();
        result.put("code", 1);
        result.put("errorCode", "CANCELLED");
        result.put("message", "已取消人脸拍照");
        finishWithResult(RESULT_CANCELED, result);
    }

    private void fail(String errorCode, String message) {
        JSONObject result = new JSONObject();
        result.put("code", -1);
        result.put("errorCode", errorCode);
        result.put("message", message);
        finishWithResult(RESULT_FIRST_USER, result);
    }

    private void finishWithResult(int resultCode, JSONObject result) {
        if (resolved) {
            return;
        }
        resolved = true;
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT_CODE, result.getIntValue("code"));
        resultIntent.putExtra(EXTRA_RESULT_ERROR_CODE, result.getString("errorCode"));
        resultIntent.putExtra(EXTRA_RESULT_MESSAGE, result.getString("message"));
        resultIntent.putExtra(EXTRA_RESULT_PATH, result.getString("path"));
        resultIntent.putExtra(EXTRA_RESULT_WIDTH, result.getIntValue("width"));
        resultIntent.putExtra(EXTRA_RESULT_HEIGHT, result.getIntValue("height"));
        setResult(resultCode, resultIntent);
        CaptureCallbackRegistry.resolve(sessionId, result);
        finish();
    }

    private void restartPreview() {
        if (camera == null) {
            openCameraIfReady();
            return;
        }
        try {
            camera.startPreview();
            previewStarted = true;
            updateControlState();
        } catch (RuntimeException exception) {
            releaseCamera();
            openCameraIfReady();
        }
    }

    private void updateControlState() {
        boolean enabled = camera != null && previewStarted && !captureInProgress;
        shutterButton.setEnabled(enabled);
        shutterButton.setAlpha(enabled ? 1f : 0.5f);
        switchButton.setEnabled(enabled);
        switchButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void releaseCamera() {
        if (camera == null) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (RuntimeException ignored) {
            // takePicture() already stops preview on many devices.
        }
        camera.release();
        camera = null;
        previewStarted = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureSystemBars();
        openCameraIfReady();
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            configureSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        if (!resolved && isFinishing()) {
            cancel();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
