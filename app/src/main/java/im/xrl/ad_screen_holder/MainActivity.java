package im.xrl.ad_screen_holder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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
    private static final int IMAGE_INTERVAL_SECONDS = 10;

    private ImageView mImageView;
    private View mFallbackView;
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
                mHandler.postDelayed(this, IMAGE_INTERVAL_SECONDS * 1000L);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullScreen();
        }
    }

    // =========================================================================
    // 键盘拦截与导航键屏蔽
    // =========================================================================

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
            if (handleKey(event.getKeyCode())) {
                return true;
            }
            int keyCode = event.getKeyCode();
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
        View decor = getWindow().getDecorView();
        if (decor == null) return;
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decor.setSystemUiVisibility(flags);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        mHandler.postDelayed(mSwitcher, IMAGE_INTERVAL_SECONDS * 1000L);
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
