package im.xrl.ad_screen_holder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主界面：全屏广告轮播 + 键盘管理入口。
 * 继承 AppCompatActivity 以使用 Material 3 组件。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AdScreenHolder";
    private static final String ADS_DIR = Utils.getAdsDir();

    private ImageView mImageView;
    private View mFallbackView;
    private ImeInterceptorView mImeInterceptor;
    private Handler mHandler;
    private List<String> mImagePaths;
    private int mCurrentIndex;
    private boolean mIsRunning;

    private final ImageSwitcherRunnable mSwitcher = new ImageSwitcherRunnable();
    private ScreenReceiver mScreenReceiver;

    /** 轮播任务 */
    private class ImageSwitcherRunnable implements Runnable {
        @Override
        public void run() {
            showNextImage();
            if (mHandler != null) {
                int interval = SettingsActivity.getImageInterval(MainActivity.this);
                mHandler.postDelayed(this, interval * 1000L);
            }
        }
    }

    /** 屏幕开关监听 */
    private class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.d(TAG, "屏幕关闭 - 暂停轮播");
                stopSlideshow();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.d(TAG, "屏幕开启 - 恢复轮播");
                startSlideshow();
            }
        }
    }

    /** 后台加载图片 */
    private class ImageLoader implements Runnable {
        private final String path;
        ImageLoader(String path) { this.path = path; }
        @Override
        public void run() {
            try {
                final Bitmap bitmap = decodeSampledBitmap(path,
                        mImageView.getWidth(), mImageView.getHeight());
                if (bitmap == null) {
                    Log.w(TAG, "无法解码图片: " + path);
                    return;
                }
                runOnUiThread(new BitmapSetter(bitmap));
            } catch (Exception e) {
                Log.e(TAG, "加载图片失败: " + path, e);
            }
        }
    }

    /** UI 线程设置图片（带淡入淡出） */
    private class BitmapSetter implements Runnable {
        private final Bitmap bitmap;
        BitmapSetter(Bitmap bitmap) { this.bitmap = bitmap; }
        @Override
        public void run() {
            mImageView.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
                @Override
                public void run() {
                    recycleCurrentBitmap();
                    mImageView.setImageBitmap(bitmap);
                    mImageView.animate().alpha(1f).setDuration(400).start();
                }
            }).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dynamic colors if available (Android 12+)
        DynamicColors.applyIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = findViewById(R.id.imageView);
        mFallbackView = findViewById(R.id.fallbackView);
        mHandler = new Handler();
        mImagePaths = new ArrayList<>();

        setupImeInterceptor();

        enterFullScreen();
        loadImageList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullScreen();
        registerScreenReceiver();
        loadImageList();
        startSlideshow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSlideshow();
        unregisterScreenReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlideshow();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        mHandler = null;
    }

    // =========================================================================
    // 键盘拦截与导航键屏蔽
    // =========================================================================

    /**
     * 创建并添加不可见的 IME 拦截视图，用于接收虚拟键盘输入。
     * 物理键盘仍走 {@link #dispatchKeyEvent} / {@link #onKeyDown} 路径。
     */
    private void setupImeInterceptor() {
        // 禁止输入法窗口自动弹出
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mImeInterceptor = new ImeInterceptorView(this);
        mImeInterceptor.setOnCharListener(new ImeInterceptorView.OnCharListener() {
            @Override
            public void onChar(char c) {
                handleImeChar(c);
            }
        });

        // 添加到根布局（1dp 不可见）
        FrameLayout root = findViewById(android.R.id.content);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        root.addView(mImeInterceptor, lp);

        // 获取焦点以接收 IME 输入，随即隐藏可能弹出的键盘
        mImeInterceptor.requestFocus();
        mImeInterceptor.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(mImeInterceptor.getWindowToken(), 0);
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullScreen();
            // 保持 IME 拦截器的焦点，同时确保键盘隐藏
            if (mImeInterceptor != null && !mImeInterceptor.hasFocus()) {
                mImeInterceptor.requestFocus();
            }
        }
    }

    /** 处理虚拟键盘来的字符，映射到对应操作 */
    private void handleImeChar(char c) {
        switch (c) {
            case 'a':
                Log.d(TAG, "IME 输入 A - 进入管理面板");
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case 'q':
                Log.d(TAG, "IME 输入 Q - 强制退出");
                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                }
                android.os.Process.killProcess(android.os.Process.myPid());
                finish();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (handleKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            // 1. 硬件键盘 keycode 优先
            if (handleKey(keyCode)) return true;
            // 2. IME sendKeyEvent 附带的字符
            String chars = event.getCharacters();
            if (chars != null && chars.length() > 0) {
                handleImeChar(Character.toLowerCase(chars.charAt(0)));
                return true;
            }
            int unicode = event.getUnicodeChar();
            if (unicode != 0) {
                handleImeChar(Character.toLowerCase((char) unicode));
                return true;
            }
            // 3. 屏蔽导航键
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_HOME
                    || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_A) {
            Log.d(TAG, "按 A 进入管理面板");
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_Q) {
            Log.d(TAG, "按 Q 强制退出");
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        // 霸屏模式：屏蔽返回键
    }

    // =========================================================================
    // 全屏沉浸模式
    // =========================================================================

    private void enterFullScreen() {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        // Android 11+ (API 30+) 使用 WindowInsetsController 可靠隐藏系统栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Android 5.0–10 (API 21–29) 使用传统 flags
            View decor = getWindow().getDecorView();
            if (decor != null) {
                int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decor.setSystemUiVisibility(flags);
            }
        }
        // 保持屏幕常亮 + 锁屏状态下也能显示
        attrs.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        getWindow().setAttributes(attrs);
    }

    // =========================================================================
    // 图片管理
    // =========================================================================

    private void loadImageList() {
        mImagePaths.clear();
        List<String> active = SettingsActivity.getActiveImages(this);
        if (!active.isEmpty()) {
            mImagePaths.addAll(active);
        } else {
            // 兼容旧逻辑：扫描目录
            File adsDir = new File(ADS_DIR);
            if (!adsDir.exists() || !adsDir.isDirectory()) {
                showFallback(getString(R.string.toast_ads_dir_not_found));
                return;
            }
            File[] files = adsDir.listFiles(Utils.IMAGE_FILTER);
            if (files == null || files.length == 0) {
                showFallback(getString(R.string.toast_no_images));
                return;
            }
            for (File f : files) {
                mImagePaths.add(f.getAbsolutePath());
            }
            Collections.sort(mImagePaths);
        }

        if (mImagePaths.isEmpty()) {
            showFallback(getString(R.string.toast_no_images));
        } else {
            hideFallback();
        }
    }

    private void showFallback(String message) {
        mImageView.setVisibility(View.INVISIBLE);
        mFallbackView.setVisibility(View.VISIBLE);
    }

    private void hideFallback() {
        mFallbackView.setVisibility(View.GONE);
        mImageView.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    // 轮播控制
    // =========================================================================

    private void startSlideshow() {
        if (mImagePaths.isEmpty() || mIsRunning) return;
        mIsRunning = true;
        hideFallback();
        showNextImage();
        int interval = SettingsActivity.getImageInterval(this);
        mHandler.postDelayed(mSwitcher, interval * 1000L);
        Log.d(TAG, "轮播开始");
    }

    private void stopSlideshow() {
        if (!mIsRunning) return;
        mIsRunning = false;
        if (mHandler != null) {
            mHandler.removeCallbacks(mSwitcher);
        }
        Log.d(TAG, "轮播暂停");
    }

    private void showNextImage() {
        if (mImagePaths.isEmpty()) return;
        if (mCurrentIndex >= mImagePaths.size()) {
            mCurrentIndex = 0;
        }
        loadImage(mImagePaths.get(mCurrentIndex));
        mCurrentIndex++;
    }

    private void loadImage(String path) {
        new Thread(new ImageLoader(path)).start();
    }

    private Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        if (reqWidth <= 0 || reqHeight <= 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            reqWidth = metrics.widthPixels;
            reqHeight = metrics.heightPixels;
        }

        options.inSampleSize = Utils.calculateSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inDither = true;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        return BitmapFactory.decodeFile(path, options);
    }

    private void recycleCurrentBitmap() {
        Drawable drawable = mImageView.getDrawable();
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap old = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            if (old != null && !old.isRecycled()) {
                old.recycle();
            }
        }
    }

    // =========================================================================
    // 屏幕开关监听
    // =========================================================================

    private void registerScreenReceiver() {
        if (mScreenReceiver == null) {
            mScreenReceiver = new ScreenReceiver();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);
    }

    private void unregisterScreenReceiver() {
        if (mScreenReceiver != null) {
            try {
                unregisterReceiver(mScreenReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "屏幕监听已注销");
            }
        }
    }
}
