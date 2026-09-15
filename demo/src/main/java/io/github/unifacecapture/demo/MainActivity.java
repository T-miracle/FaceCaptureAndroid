package io.github.unifacecapture.demo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.unifacecapture.FaceCaptureActivity;

/** Standalone Android host for camera and crop debugging. */
public final class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST_CODE = 7101;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));

        Button button = new Button(this);
        button.setText("打开人脸拍照");
        button.setOnClickListener(view -> startActivityForResult(
            new Intent(this, FaceCaptureActivity.class),
            CAPTURE_REQUEST_CODE
        ));
        root.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));

        resultView = new TextView(this);
        resultView.setText("尚未拍照");
        resultView.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.topMargin = dp(24);
        root.addView(resultView, resultParams);
        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CAPTURE_REQUEST_CODE || data == null) {
            return;
        }
        resultView.setText(
            "resultCode=" + resultCode
                + "\n业务状态=" + data.getIntExtra(FaceCaptureActivity.EXTRA_RESULT_CODE, -999)
                + "\n照片路径=" + data.getStringExtra(FaceCaptureActivity.EXTRA_RESULT_PATH)
                + "\n尺寸=" + data.getIntExtra(FaceCaptureActivity.EXTRA_RESULT_WIDTH, 0)
                + "×" + data.getIntExtra(FaceCaptureActivity.EXTRA_RESULT_HEIGHT, 0)
                + "\n错误=" + data.getStringExtra(FaceCaptureActivity.EXTRA_RESULT_ERROR_CODE)
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
