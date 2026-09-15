# Uni-App 人脸拍照插件（Android）

面向传统 Uni-App（Vue 2 / App-Plus）的 Android 原生 Module。插件固定使用竖屏和前置摄像头，隐藏状态栏；正方形取景框宽度为屏幕宽度的 75%，图 1 作为取景框顶层引导图，拍照按钮位于取景框下方。

## 插件信息

- 插件 ID / Module 名称：`uni-face-capture`
- Android Module 类：`io.github.unifacecapture.FaceCaptureModule`
- AAR：`uni-face-capture-release.aar`
- 最低 Android：API 21

## GitHub Actions 构建

仓库 Actions Variables 需要配置：

- `DCLOUD_ANDROID_SDK_REPOSITORY`：保存 DCloud Android SDK Release 的仓库，例如 `owner/FaceCaptureAndroid`。
- `DCLOUD_ANDROID_SDK_RELEASE_TAG`：Release 标签，例如 `dcloud-sdk-android`。

对应 Release 必须包含 `uniapp-v8-release.aar`。工作流 `.github/workflows/build-android-plugin.yml` 会下载该编译依赖、构建 Release AAR，并上传 `UniFaceCapture-android-plugin` Artifact。压缩包内是：

```text
artifact/
├── android/uni-face-capture-release.aar
└── package.json
```

把 `artifact` 目录重命名为 `uni-face-capture` 后放到 Uni-App 项目的 `nativeplugins/`。宿主需要声明相机权限并重新制作自定义基座或云打包。

## Uni-App 调用

```js
const faceCapture = uni.requireNativePlugin('uni-face-capture');

faceCapture.capture({}, result => {
    if (result.code === 0) {
        // result.path：正方形 JPEG 的本地绝对路径
        // result.uri：file:// URI
        // result.width / result.height：输出像素尺寸
    }
});
```

回调只执行一次：`code: 0` 表示成功，`code: 1` 表示用户取消，`code: -1` 表示权限、设备或处理失败。引导图只覆盖相机预览，不会合成到输出照片。
