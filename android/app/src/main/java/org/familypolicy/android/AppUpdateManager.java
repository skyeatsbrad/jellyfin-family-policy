package org.familypolicy.android;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class AppUpdateManager {
    public static final String EXTRA_CHECK_UPDATES = "check_updates";
    private static final int UPDATE_JOB_ID = 0x465041;
    private static final int UPDATE_NOTIFICATION_ID = 0x465042;
    private static final String CHANNEL_ID = "family_policy_updates";
    private static final String UPDATE_FILE = "family-policy-update.apk";
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;

    private AppUpdateManager() {
    }

    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(UPDATE_JOB_ID) != null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(
            UPDATE_JOB_ID,
            new ComponentName(context, UpdateJobService.class)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(24L * 60L * 60L * 1000L)
            .setPersisted(true)
            .build();
        scheduler.schedule(job);
    }

    public static void notifyUpdate(Context context, GitHubUpdateClient.UpdateInfo update) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID,
            "Family Policy updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ));
        Intent open = new Intent(context, MainActivity.class)
            .putExtra(EXTRA_CHECK_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        manager.notify(
            UPDATE_NOTIFICATION_ID,
            new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Family Policy update available")
                .setContentText("Version " + update.versionName + " is ready to install.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        );
    }

    public static File downloadAndVerify(Context context, GitHubUpdateClient.UpdateInfo update)
        throws IOException, NoSuchAlgorithmException {
        if (update.size > MAX_APK_BYTES) {
            throw new IOException("The update APK is unexpectedly large.");
        }
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null && (directory = context.getFilesDir()) == null) {
            throw new IOException("Android did not provide a private update directory.");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create the private update directory.");
        }
        File target = new File(directory, UPDATE_FILE);
        File temporary = new File(directory, UPDATE_FILE + ".part");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Unable to replace the previous update download.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(update.downloadUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "Family-Policy-Android/" + JellyfinClient.VERSION);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("APK download failed with HTTP " + status + ".");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long bytes = 0;
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                bytes += read;
                if (bytes > MAX_APK_BYTES) {
                    throw new IOException("The update APK exceeded the download limit.");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }
        if (update.size >= 0 && bytes != update.size) {
            temporary.delete();
            throw new IOException("The update download size did not match GitHub.");
        }
        String actual = hex(digest.digest());
        if (!actual.equals(update.sha256)) {
            temporary.delete();
            throw new IOException("The update failed SHA-256 verification.");
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Unable to replace the previous verified update.");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to finalize the verified update.");
        }
        return target;
    }

    public static boolean requestInstallPermission(Activity activity) {
        if (activity.getPackageManager().canRequestPackageInstalls()) {
            return true;
        }
        Intent settings = new Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + activity.getPackageName())
        );
        activity.startActivity(settings);
        return false;
    }

    public static void install(Activity activity, File apk) {
        if (apk == null || !apk.equals(updateFile(activity)) || !apk.isFile()) {
            throw new IllegalArgumentException("The verified update APK is unavailable.");
        }
        Uri uri = Uri.parse("content://org.familypolicy.android.updates/apk");
        Intent install = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        install.setClipData(ClipData.newRawUri("Family Policy update", uri));
        activity.startActivity(install);
    }

    public static File updateFile(Context context) {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) {
            directory = context.getFilesDir();
        }
        return new File(directory, UPDATE_FILE);
    }

    @SuppressWarnings("deprecation")
    public static int installedVersionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? (int) info.getLongVersionCode()
            : info.versionCode;
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }
}
