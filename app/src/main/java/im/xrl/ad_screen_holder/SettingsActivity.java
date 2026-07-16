package im.xrl.ad_screen_holder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理面板：设置自动开关机时间、添加/选择广告图片。
 * 从主界面按键盘 A 键进入。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "AdScreenHolder";
    private static final String PREFS_NAME = "im.xrl.ad_screen_holder.prefs";
    private static final String KEY_POWER_ON_TIME = "power_on_time";
    private static final String KEY_POWER_OFF_TIME = "power_off_time";
    private static final String KEY_ACTIVE_IMAGES = "active_images";
    private static final String KEY_IMAGE_ORDER = "image_order";
    private static final String KEY_IMAGE_INTERVAL = "image_interval";
    private static final String DEFAULT_POWER_ON_TIME = "08:00";
    private static final String DEFAULT_POWER_OFF_TIME = "22:00";
    private static final int DEFAULT_IMAGE_INTERVAL = 10;
    private static final int MIN_IMAGE_INTERVAL = 3;
    private static final int MAX_IMAGE_INTERVAL = 120;
    private static final int THUMB_SIZE = 96;
    private static final int REQUEST_CODE_PICK_IMAGES = 1001;
    private static final int REQUEST_CODE_PERMISSION = 2001;
    private static final int REQUEST_CODE_DEVICE_ADMIN = 3001;

    private SharedPreferences mPrefs;
    private ImeInterceptorView mImeInterceptor;
    private ComponentName mAdminComponent;
    private TextView mPowerOnValue;
    private TextView mPowerOffValue;
    private int mPowerOnHour = 8;
    private int mPowerOnMinute = 0;
    private int mPowerOffHour = 22;
    private int mPowerOffMinute = 0;
    private LinearLayout mImageListContainer;
    private TextView mIntervalValue;
    private int mImageInterval = DEFAULT_IMAGE_INTERVAL;

    private final List<MaterialCheckBox> mImageCheckBoxes = new ArrayList<>();
    private final List<ImageView> mImageThumbs = new ArrayList<>();
    private final List<String> mImageFileNames = new ArrayList<>();
    private final LruCache<String, Bitmap> mThumbCache =
            new LruCache<String, Bitmap>(256 * 1024 * 4) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private final ExecutorService mThumbExecutor = Executors.newFixedThreadPool(2);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);
        bindViews();
        loadSettings();
        loadImageInterval();
        loadImageList();
        setupImeInterceptor();
        enterFullScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mThumbExecutor.shutdownNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullScreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullScreen();
            // 保持 IME 拦截器的焦点
            if (mImeInterceptor != null && !mImeInterceptor.hasFocus()) {
                mImeInterceptor.requestFocus();
            }
        }
    }

    private void bindViews() {
        mPowerOnValue = findViewById(R.id.powerOnValue);
        mPowerOffValue = findViewById(R.id.powerOffValue);
        mImageListContainer = findViewById(R.id.imageListContainer);
        mIntervalValue = findViewById(R.id.intervalValue);

        MaterialCardView powerOnCard = findViewById(R.id.powerOnCard);
        MaterialCardView powerOffCard = findViewById(R.id.powerOffCard);

        powerOnCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { pickTime(true); }
        });
        powerOffCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { pickTime(false); }
        });

        findViewById(R.id.btnIntervalMinus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageInterval > MIN_IMAGE_INTERVAL) {
                    mImageInterval--;
                    updateIntervalDisplay();
                }
            }
        });
        findViewById(R.id.btnIntervalPlus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageInterval < MAX_IMAGE_INTERVAL) {
                    mImageInterval++;
                    updateIntervalDisplay();
                }
            }
        });

        findViewById(R.id.btnAddImages).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { requestAddImages(); }
        });
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onSave(); }
        });
        findViewById(R.id.btnReturn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnForceQuit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { forceQuit(); }
        });
    }

    private void loadSettings() {
        int[] on = parseTime(mPrefs.getString(KEY_POWER_ON_TIME, DEFAULT_POWER_ON_TIME), 8, 0);
        int[] off = parseTime(mPrefs.getString(KEY_POWER_OFF_TIME, DEFAULT_POWER_OFF_TIME), 22, 0);
        mPowerOnHour = on[0];
        mPowerOnMinute = on[1];
        mPowerOffHour = off[0];
        mPowerOffMinute = off[1];
        updateTimeDisplay();
    }

    private void loadImageInterval() {
        mImageInterval = mPrefs.getInt(KEY_IMAGE_INTERVAL, DEFAULT_IMAGE_INTERVAL);
        if (mImageInterval < MIN_IMAGE_INTERVAL) mImageInterval = MIN_IMAGE_INTERVAL;
        if (mImageInterval > MAX_IMAGE_INTERVAL) mImageInterval = MAX_IMAGE_INTERVAL;
        updateIntervalDisplay();
    }

    private void updateIntervalDisplay() {
        if (mIntervalValue != null) {
            mIntervalValue.setText(getString(R.string.switch_interval_seconds, mImageInterval));
        }
    }

    private void updateTimeDisplay() {
        mPowerOnValue.setText(String.format(Locale.getDefault(), getString(R.string.power_time_format), mPowerOnHour, mPowerOnMinute));
        mPowerOffValue.setText(String.format(Locale.getDefault(), getString(R.string.power_time_format), mPowerOffHour, mPowerOffMinute));
    }

    private int[] parseTime(String time, int defHour, int defMinute) {
        if (TextUtils.isEmpty(time) || !time.contains(":")) {
            return new int[]{defHour, defMinute};
        }
        String[] parts = time.split(":");
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return new int[]{defHour, defMinute};
        }
    }

    private void pickTime(final boolean isPowerOn) {
        int hour = isPowerOn ? mPowerOnHour : mPowerOffHour;
        int minute = isPowerOn ? mPowerOnMinute : mPowerOffMinute;

        TimePickerDialog dialog = new TimePickerDialog(this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        if (isPowerOn) {
                            mPowerOnHour = hourOfDay;
                            mPowerOnMinute = minute;
                        } else {
                            mPowerOffHour = hourOfDay;
                            mPowerOffMinute = minute;
                        }
                        updateTimeDisplay();
                    }
                }, hour, minute, true);
        dialog.setTitle(isPowerOn ? R.string.power_on : R.string.power_off);
        dialog.show();
    }

    // =========================================================================
    // 图片管理
    // =========================================================================

    private void requestAddImages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_PERMISSION);
                return;
            }
        }
        openImagePicker();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.btn_add_images)),
                REQUEST_CODE_PICK_IMAGES);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            }
            if (granted) {
                openImagePicker();
            } else {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_DEVICE_ADMIN) {
            if (isDeviceAdminActive()) {
                Toast.makeText(this, R.string.toast_device_admin_enabled, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.toast_device_admin_denied, Toast.LENGTH_LONG).show();
            }
            finishSave();
            return;
        }
        if (requestCode == REQUEST_CODE_PICK_IMAGES && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                copyImagesToAdsDir(uris);
            }
        }
    }

    @SuppressLint("SetWorldReadable")
    private void copyImagesToAdsDir(List<Uri> uris) {
        File adsDir = new File(Utils.getAdsDir());
        if (!adsDir.exists() && !adsDir.mkdirs()) {
            Toast.makeText(this, R.string.toast_image_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> addedNames = new ArrayList<>();
        for (Uri uri : uris) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) continue;
                String ext = Utils.getImageExtension(this, uri);
                String original = Utils.getFileName(this, uri);
                String base = stripExt(original);
                if (base.isEmpty()) base = "image";
                String name = uniquify(adsDir, base, ext);
                File outFile = new File(adsDir, name);
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                outFile.setReadable(true, false);
                addedNames.add(name);
            } catch (IOException e) {
                Log.e(TAG, "复制图片失败: " + uri, e);
            }
        }

        if (!addedNames.isEmpty()) {
            // 默认让新增图片进入“展示”列表
            String existing = mPrefs.getString(KEY_ACTIVE_IMAGES, "");
            List<String> active = new ArrayList<>();
            if (!TextUtils.isEmpty(existing)) {
                for (String s : existing.split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) active.add(s);
                }
            }
            for (String n : addedNames) {
                if (!active.contains(n)) active.add(n);
            }
            mPrefs.edit().putString(KEY_ACTIVE_IMAGES, TextUtils.join(",", active)).apply();
            Toast.makeText(this, getString(R.string.toast_image_added, addedNames.size()),
                    Toast.LENGTH_SHORT).show();
            loadImageList();
        } else {
            Toast.makeText(this, R.string.toast_image_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String stripExt(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) fileName = fileName.substring(0, dot);
        return fileName.trim();
    }

    private String uniquify(File dir, String base, String ext) {
        // 过滤掉不合法文件名字符
        String safe = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safe.isEmpty()) safe = "image";
        String candidate = safe + ext;
        if (!new File(dir, candidate).exists()) return candidate;
        for (int i = 1; i < 1000; i++) {
            candidate = safe + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) return candidate;
        }
        return safe + "_" + System.currentTimeMillis() + ext;
    }

    private void loadImageList() {
        mImageListContainer.removeAllViews();
        mImageCheckBoxes.clear();
        mImageThumbs.clear();
        mImageFileNames.clear();

        File adsDir = new File(Utils.getAdsDir());
        if (!adsDir.exists() || !adsDir.isDirectory()) {
            addEmptyHint(getString(R.string.ad_images_empty));
            return;
        }

        File[] files = adsDir.listFiles(Utils.IMAGE_FILTER);
        if (files == null || files.length == 0) {
            addEmptyHint(getString(R.string.ad_images_empty));
            return;
        }

        List<File> imageFiles = new ArrayList<>(Arrays.asList(files));
        Collections.sort(imageFiles, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        String savedActive = mPrefs.getString(KEY_ACTIVE_IMAGES, "");
        List<String> activeList = new ArrayList<>();
        if (!TextUtils.isEmpty(savedActive)) {
            activeList.addAll(Arrays.asList(savedActive.split(",")));
        }
        boolean hasSaved = mPrefs.contains(KEY_ACTIVE_IMAGES);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final File file : imageFiles) {
            final String fileName = file.getName();
            mImageFileNames.add(fileName);

            @SuppressLint("InflateParams")
            MaterialCardView card = (MaterialCardView) inflater.inflate(R.layout.item_image, mImageListContainer, false);

            TextView name = card.findViewById(R.id.imageName);
            name.setText(fileName);

            ImageView thumb = card.findViewById(R.id.imageThumb);
            mImageThumbs.add(thumb);
            thumb.setTag(file.getAbsolutePath());
            thumb.setImageDrawable(null);
            Bitmap cached = mThumbCache.get(file.getAbsolutePath());
            if (cached != null) {
                thumb.setImageBitmap(cached);
            } else {
                loadThumbnailAsync(thumb, file);
            }

            MaterialCheckBox check = card.findViewById(R.id.imageCheck);
            check.setChecked(hasSaved ? activeList.contains(fileName) : true);
            mImageCheckBoxes.add(check);

            View delete = card.findViewById(R.id.imageDelete);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(file);
                }
            });

            card.setCardBackgroundColor(getResources().getColor(R.color.md_theme_surfaceVariant));

            mImageListContainer.addView(card);
        }
    }

    private void loadThumbnailAsync(final ImageView target, final File file) {
        final String path = file.getAbsolutePath();
        mThumbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap thumb = decodeThumb(file);
                if (thumb != null) {
                    mThumbCache.put(path, thumb);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (path.equals(target.getTag())) {
                                target.setImageBitmap(thumb);
                            }
                        }
                    });
                }
            }
        });
    }

    private Bitmap decodeThumb(File file) {
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int halfW = Math.max(1, opts.outWidth / 2);
            int halfH = Math.max(1, opts.outHeight / 2);
            int sample = 1;
            while ((halfW / sample) > THUMB_SIZE || (halfH / sample) > THUMB_SIZE) {
                sample *= 2;
            }
            opts.inSampleSize = sample;
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable t) {
            Log.w(TAG, "缩略图解码失败: " + file.getAbsolutePath(), t);
            return null;
        }
    }

    private void confirmDelete(final File file) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete_image, file.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.btn_delete, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        deleteImage(file);
                    }
                })
                .show();
    }

    private void deleteImage(File file) {
        String path = file.getAbsolutePath();
        boolean deleted = false;
        try {
            deleted = file.delete();
        } catch (Throwable t) {
            Log.w(TAG, "删除失败: " + path, t);
        }
        if (deleted) {
            mThumbCache.remove(path);
            // 从 active_images 中移除，避免残留
            String savedActive = mPrefs.getString(KEY_ACTIVE_IMAGES, "");
            if (!TextUtils.isEmpty(savedActive)) {
                List<String> list = new ArrayList<>(Arrays.asList(savedActive.split(",")));
                list.remove(file.getName());
                mPrefs.edit().putString(KEY_ACTIVE_IMAGES, TextUtils.join(",", list)).apply();
            }
            Toast.makeText(this, R.string.toast_image_deleted, Toast.LENGTH_SHORT).show();
            loadImageList();
        } else {
            Toast.makeText(this, R.string.toast_image_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void addEmptyHint(String message) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_image_empty, mImageListContainer, false);
        ((TextView) view.findViewById(R.id.emptyText)).setText(message);
        mImageListContainer.addView(view);
    }

    // =========================================================================
    // 保存与退出
    // =========================================================================

    private void onSave() {
        String powerOn = String.format(Locale.getDefault(), "%02d:%02d", mPowerOnHour, mPowerOnMinute);
        String powerOff = String.format(Locale.getDefault(), "%02d:%02d", mPowerOffHour, mPowerOffMinute);

        StringBuilder sb = new StringBuilder();
        int selectedCount = 0;
        for (int i = 0; i < mImageFileNames.size(); i++) {
            if (mImageCheckBoxes.get(i).isChecked()) {
                selectedCount++;
                if (sb.length() > 0) sb.append(",");
                sb.append(mImageFileNames.get(i));
            }
        }

        if (selectedCount == 0 && !mImageFileNames.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_image_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        final String imageList = sb.toString();
        mPrefs.edit()
                .putString(KEY_POWER_ON_TIME, powerOn)
                .putString(KEY_POWER_OFF_TIME, powerOff)
                .putString(KEY_ACTIVE_IMAGES, imageList)
                .putString(KEY_IMAGE_ORDER, imageList)
                .putInt(KEY_IMAGE_INTERVAL, mImageInterval)
                .apply();

        // 自动关机依赖设备管理器权限。未激活则引导用户开启后再完成退出
        if (!isDeviceAdminActive()) {
            requestDeviceAdmin();
            return;
        }

        finishSave();
    }

    private boolean isDeviceAdminActive() {
        if (mAdminComponent == null) return false;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(mAdminComponent);
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation));
        try {
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toast_device_admin_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void finishSave() {
        startService(new Intent(this, AutoPowerService.class));
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void forceQuit() {
        android.os.Process.killProcess(android.os.Process.myPid());
        finish();
    }

    // =========================================================================
    // 键盘快捷键
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

        // 添加到根布局
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

    /** 处理虚拟键盘来的字符，映射到对应操作 */
    private void handleImeChar(char c) {
        switch (c) {
            case 'a':
                Log.d(TAG, "IME 输入 A - 返回轮播（不保存）");
                finish();
                break;
            case 'q':
                Log.d(TAG, "IME 输入 Q - 强制退出");
                forceQuit();
                break;
            case 's':
                Log.d(TAG, "IME 输入 S - 保存并返回");
                onSave();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (handleKey(event.getKeyCode())) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_Q:
                forceQuit();
                return true;
            case KeyEvent.KEYCODE_A:
                finish();
                return true;
            case KeyEvent.KEYCODE_S:
                onSave();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onBackPressed() {
        // 霸屏模式：屏蔽返回键
    }

    // =========================================================================
    // 全屏沉浸
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
    // 对外 API
    // =========================================================================

    public static List<String> getActiveImages(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_ACTIVE_IMAGES, "");
        List<String> result = new ArrayList<>();
        if (!TextUtils.isEmpty(saved)) {
            for (String s : saved.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) {
                    result.add(Utils.getAdsDir() + s);
                }
            }
        }
        return result;
    }

    public static String getPowerOnTime(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_POWER_ON_TIME, DEFAULT_POWER_ON_TIME);
    }

    public static String getPowerOffTime(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_POWER_OFF_TIME, DEFAULT_POWER_OFF_TIME);
    }

    public static int getImageInterval(Context context) {
        int v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_IMAGE_INTERVAL, DEFAULT_IMAGE_INTERVAL);
        if (v < MIN_IMAGE_INTERVAL) v = MIN_IMAGE_INTERVAL;
        if (v > MAX_IMAGE_INTERVAL) v = MAX_IMAGE_INTERVAL;
        return v;
    }
}
