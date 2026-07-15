package im.xrl.ad_screen_holder;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Locale;

/**
 * 通用工具类：图片过滤、路径处理、采样率计算。
 */
public final class Utils {

    private Utils() {}

    public static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"};

    public static final FilenameFilter IMAGE_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            String lower = name.toLowerCase(Locale.US);
            for (String ext : IMAGE_EXTENSIONS) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        }
    };

    public static String getAdsDir() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/ads/";
    }

    public static int calculateSampleSize(int rawWidth, int rawHeight, int reqWidth, int reqHeight) {
        int sampleSize = 1;
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            final int halfHeight = rawHeight / 2;
            final int halfWidth = rawWidth / 2;
            while ((halfHeight / sampleSize) >= reqHeight
                    && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2;
            }
        }
        return sampleSize;
    }

    public static String getImageExtension(Context context, Uri uri) {
        String ext = ".jpg";
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            String type = context.getContentResolver().getType(uri);
            if (!TextUtils.isEmpty(type)) {
                ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                if (ext == null || ext.isEmpty()) ext = "jpg";
                ext = "." + ext;
            }
        } else {
            String path = uri.getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot > 0) ext = path.substring(dot);
            }
        }
        String lower = ext.toLowerCase(Locale.US);
        boolean valid = false;
        for (String e : IMAGE_EXTENSIONS) {
            if (lower.equals(e)) { valid = true; break; }
        }
        return valid ? lower : ".jpg";
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result == null ? "unknown" : result;
    }
}
