package im.xrl.ad_screen_holder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import java.util.Calendar;

/**
 * 自动开关屏服务：按设定时间点亮/熄灭屏幕。
 *
 * 排程（{@link #scheduleAlarms()}）会在每次开机、时间/时区变更、用户保存设定时触发；
 * 实际的开/关屏动作由 {@link AlarmReceiver} 同步完成，避免 Android 8+ 后台 Service 限制。
 */
public class AutoPowerService extends Service {

    static final String ACTION_POWER_ON_ALARM = "im.xrl.ad_screen_holder.action.POWER_ON_ALARM";
    static final String ACTION_POWER_OFF_ALARM = "im.xrl.ad_screen_holder.action.POWER_OFF_ALARM";

    private static final int REQUEST_POWER_ON = 7001;
    private static final int REQUEST_POWER_OFF = 7002;

    private AlarmManager mAlarmManager;
    private BroadcastReceiver mTimeChangeReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        mTimeChangeReceiver = new TimeChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mTimeChangeReceiver, filter);

        scheduleAlarms();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleAlarms();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAlarms();
        if (mTimeChangeReceiver != null) {
            try {
                unregisterReceiver(mTimeChangeReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            mTimeChangeReceiver = null;
        }
    }

    private void scheduleAlarms() {
        cancelAlarms();
        String onTime = SettingsActivity.getPowerOnTime(this);
        String offTime = SettingsActivity.getPowerOffTime(this);

        mAlarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                getNextTriggerTime(onTime, "08:00"),
                AlarmManager.INTERVAL_DAY,
                buildPendingIntent(ACTION_POWER_ON_ALARM, REQUEST_POWER_ON));

        mAlarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                getNextTriggerTime(offTime, "22:00"),
                AlarmManager.INTERVAL_DAY,
                buildPendingIntent(ACTION_POWER_OFF_ALARM, REQUEST_POWER_OFF));
    }

    private void cancelAlarms() {
        mAlarmManager.cancel(buildPendingIntent(ACTION_POWER_ON_ALARM, REQUEST_POWER_ON));
        mAlarmManager.cancel(buildPendingIntent(ACTION_POWER_OFF_ALARM, REQUEST_POWER_OFF));
    }

    private PendingIntent buildPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }

    long getNextTriggerTime(String timeStr, String defaultTime) {
        if (TextUtils.isEmpty(timeStr) || !timeStr.contains(":")) {
            timeStr = defaultTime;
        }
        String[] parts = timeStr.split(":");
        int hour, minute;
        try {
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            hour = 8;
            minute = 0;
        }

        Calendar now = Calendar.getInstance();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);

        if (!trigger.after(now)) {
            trigger.add(Calendar.DAY_OF_MONTH, 1);
        }
        return trigger.getTimeInMillis();
    }

    private class TimeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            scheduleAlarms();
        }
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        private static final String WAKE_LOCK_TAG = "AutoPowerService:ScreenWake";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ACTION_POWER_ON_ALARM.equals(action)) {
                turnScreenOn(context);
            } else if (ACTION_POWER_OFF_ALARM.equals(action)) {
                turnScreenOff(context);
            }
        }

        private void turnScreenOn(Context context) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    WAKE_LOCK_TAG);
            wl.acquire(10000);
            wl.release();
        }

        private void turnScreenOff(Context context) {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                try {
                    ComponentName adminComponent = new ComponentName(context, DeviceAdminReceiver.class);
                    if (dpm.isAdminActive(adminComponent)) {
                        dpm.lockNow();
                    }
                } catch (SecurityException ignored) {
                }
            }
        }
    }
}
