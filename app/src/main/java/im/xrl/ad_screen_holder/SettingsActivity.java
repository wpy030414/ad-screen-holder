package im.xrl.ad_screen_holder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
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
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
    private static final String DEFAULT_POWER_ON_TIME = "08:00";
    private static final String DEFAULT_POWER_OFF_TIME = "22:00";
    private static final int REQUEST_CODE_PICK_IMAGES = 1001;
    private static final int REQUEST_CODE_PERMISSION = 2001;

    private SharedPreferences mPrefs;
    private TextView mPowerOnValue;
    private TextView mPowerOffValue;
    private int mPowerOnHour = 8;
    private int mPowerOnMinute = 0;
    private int mPowerOffHour = 22;
    private int mPowerOffMinute = 0;
    private LinearLayout mImageListContainer;

    private final List<MaterialRadioButton> mImageRadioButtons = new ArrayList<>();
    private final List<MaterialCheckBox> mImageCheckBoxes = new ArrayList<>();
    private final List<String> mImageFileNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        bindViews();
        loadSettings();
        loadImageList();
        enterFullScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullScreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullScreen();
    }

    private void bindViews() {
        mPowerOnValue = findViewById(R.id.powerOnValue);
        mPowerOffValue = findViewById(R.id.powerOffValue);
        mImageListContainer = findViewById(R.id.imageListContainer);

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

        int successCount = 0;
        for (Uri uri : uris) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) continue;
                String ext = Utils.getImageExtension(this, uri);
                String name = System.currentTimeMillis() + "_" + successCount + ext;
                File outFile = new File(adsDir, name);
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                outFile.setReadable(true, false);
                successCount++;
            } catch (IOException e) {
                Log.e(TAG, "复制图片失败: " + uri, e);
            }
        }

        if (successCount > 0) {
            Toast.makeText(this, getString(R.string.toast_image_added, successCount), Toast.LENGTH_SHORT).show();
            loadImageList();
        } else {
            Toast.makeText(this, R.string.toast_image_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImageList() {
        mImageListContainer.removeAllViews();
        mImageRadioButtons.clear();
        mImageCheckBoxes.clear();
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
        for (File file : imageFiles) {
            final String fileName = file.getName();
            mImageFileNames.add(fileName);

            @SuppressLint("InflateParams")
            LinearLayout row = (LinearLayout) inflater.inflate(R.layout.item_image, mImageListContainer, false);

            MaterialRadioButton radio = row.findViewById(R.id.imageRadio);
            radio.setText(fileName);
            radio.setOnClickListener(mRadioGroupListener);
            mImageRadioButtons.add(radio);

            MaterialCheckBox check = row.findViewById(R.id.imageCheck);
            check.setChecked(hasSaved ? activeList.contains(fileName) : true);
            mImageCheckBoxes.add(check);

            MaterialCardView card = row.findViewById(R.id.imageCard);
            card.setCardBackgroundColor(getResources().getColor(R.color.md_theme_surfaceVariant));

            mImageListContainer.addView(row);
        }

        if (!mImageRadioButtons.isEmpty()) {
            mImageRadioButtons.get(0).setChecked(true);
        }
    }

    private void addEmptyHint(String message) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_image_empty, mImageListContainer, false);
        ((TextView) view.findViewById(R.id.emptyText)).setText(message);
        mImageListContainer.addView(view);
    }

    private final View.OnClickListener mRadioGroupListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MaterialRadioButton clicked = (MaterialRadioButton) v;
            for (MaterialRadioButton rb : mImageRadioButtons) {
                rb.setChecked(rb == clicked);
            }
        }
    };

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

        String imageList = sb.toString();
        mPrefs.edit()
                .putString(KEY_POWER_ON_TIME, powerOn)
                .putString(KEY_POWER_OFF_TIME, powerOff)
                .putString(KEY_ACTIVE_IMAGES, imageList)
                .putString(KEY_IMAGE_ORDER, imageList)
                .apply();

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
}
