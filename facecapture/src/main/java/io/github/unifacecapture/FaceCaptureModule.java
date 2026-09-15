package io.github.unifacecapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.alibaba.fastjson.JSONObject;

import java.util.UUID;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

/** Uni-App entry point for the native face capture screen. */
public final class FaceCaptureModule extends UniModule {
    public static final String MODULE_NAME = "uni-face-capture";

    /**
     * Opens the portrait face capture screen.
     *
     * @param options reserved for future compatible options
     * @param callback receives {code, path, uri, width, height}
     */
    @UniJSMethod(uiThread = true)
    public void capture(JSONObject options, UniJSCallback callback) {
        if (callback == null) {
            return;
        }
        Context context = mUniSDKInstance == null ? null : mUniSDKInstance.getContext();
        if (context == null) {
            callback.invoke(error("CONTEXT_UNAVAILABLE", "无法启动人脸拍照页面"));
            return;
        }

        String sessionId = UUID.randomUUID().toString();
        if (!CaptureCallbackRegistry.register(sessionId, callback::invoke)) {
            callback.invoke(error("CAPTURE_ALREADY_OPEN", "人脸拍照页面已打开"));
            return;
        }

        Intent intent = new Intent(context, FaceCaptureActivity.class);
        intent.putExtra(FaceCaptureActivity.EXTRA_SESSION_ID, sessionId);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            CaptureCallbackRegistry.resolve(
                sessionId,
                error("OPEN_FAILED", "人脸拍照页面启动失败")
            );
        }
    }

    private JSONObject error(String errorCode, String message) {
        JSONObject result = new JSONObject();
        result.put("code", -1);
        result.put("errorCode", errorCode);
        result.put("message", message);
        return result;
    }
}
