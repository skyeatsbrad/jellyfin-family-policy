package org.familypolicy.android;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class AppUpdateManagerDeviceTest {
    @Test
    public void testDownloadProviderAndInstallerHandoff() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<Activity> activityReference = new AtomicReference<>();
            scenario.onActivity(activityReference::set);
            Activity activity = activityReference.get();
            assertNotNull(activity);

            File installedApk = new File(activity.getApplicationInfo().sourceDir);
            String expectedSha256 = sha256(installedApk);
            try (LocalApkServer server = new LocalApkServer(installedApk)) {
                GitHubUpdateClient.UpdateInfo update = new GitHubUpdateClient.UpdateInfo(
                    2,
                    "device-test",
                    "family-policy-android-v2-device-test.apk",
                    server.url(),
                    expectedSha256,
                    installedApk.length()
                );

                File apk = AppUpdateManager.downloadAndVerify(activity, update);
                server.assertSucceeded();
                assertTrue(apk.isFile());
                assertEquals(installedApk.length(), apk.length());
                assertEquals(expectedSha256, sha256(apk));

                Uri uri = Uri.parse("content://org.familypolicy.android.updates/apk");
                try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
                    assertNotNull(cursor);
                    assertTrue(cursor.moveToFirst());
                    assertEquals(
                        "family-policy-update.apk",
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    );
                    assertEquals(
                        installedApk.length(),
                        cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                    );
                }
                try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                    assertNotNull(input);
                    assertTrue(input.read() >= 0);
                }

                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> AppUpdateManager.install(activity, apk)
                );
                Thread.sleep(8_000);
            }
        }
    }

    @Test
    public void testDigestMismatchIsRejected() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<Activity> activityReference = new AtomicReference<>();
            scenario.onActivity(activityReference::set);
            Activity activity = activityReference.get();
            assertNotNull(activity);

            File updateFile = AppUpdateManager.updateFile(activity);
            assertTrue(!updateFile.exists() || updateFile.delete());
            File installedApk = new File(activity.getApplicationInfo().sourceDir);
            try (LocalApkServer server = new LocalApkServer(installedApk)) {
                GitHubUpdateClient.UpdateInfo update = new GitHubUpdateClient.UpdateInfo(
                    2,
                    "device-test",
                    "family-policy-android-v2-device-test.apk",
                    server.url(),
                    "00".repeat(32),
                    installedApk.length()
                );

                try {
                    AppUpdateManager.downloadAndVerify(activity, update);
                    fail("Expected a SHA-256 mismatch.");
                } catch (IOException ex) {
                    assertTrue(ex.getMessage().contains("SHA-256"));
                }
                server.assertSucceeded();
            }
            assertFalse(updateFile.exists());
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }

    private static final class LocalApkServer implements AutoCloseable {
        private final File apk;
        private final ServerSocket server;
        private final Thread worker;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        LocalApkServer(File apk) throws IOException {
            this.apk = apk;
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(this::serve, "family-policy-apk-server");
            worker.setDaemon(true);
            worker.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/update.apk";
        }

        void assertSucceeded() throws Exception {
            worker.join(5_000);
            if (worker.isAlive()) {
                fail("The test APK server did not finish.");
            }
            Throwable error = failure.get();
            if (error != null) {
                throw new Exception("The test APK server failed.", error);
            }
        }

        private void serve() {
            try (
                Socket socket = server.accept();
                BufferedReader request = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets.US_ASCII
                ));
                FileInputStream input = new FileInputStream(apk);
                OutputStream output = socket.getOutputStream()
            ) {
                while (true) {
                    String line = request.readLine();
                    if (line == null || line.isEmpty()) {
                        break;
                    }
                }
                output.write((
                    "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/vnd.android.package-archive\r\n"
                        + "Content-Length: " + apk.length() + "\r\n"
                        + "Connection: close\r\n\r\n"
                ).getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            } catch (Throwable error) {
                failure.set(error);
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            worker.join(5_000);
        }
    }
}
