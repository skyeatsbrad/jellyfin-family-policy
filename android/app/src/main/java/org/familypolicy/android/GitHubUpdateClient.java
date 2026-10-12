package org.familypolicy.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubUpdateClient {
    static final String RELEASES_URL =
        "https://api.github.com/repos/skyeatsbrad/jellyfin-family-policy/releases?per_page=20";
    private static final Pattern APK_NAME =
        Pattern.compile("^family-policy-android-v(\\d+)-(.+)\\.apk$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA256 = Pattern.compile("^sha256:([0-9a-fA-F]{64})$");

    public UpdateInfo findUpdate(int currentVersionCode) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Family-Policy-Android/" + JellyfinClient.VERSION);
        int status = connection.getResponseCode();
        String raw;
        try {
            raw = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        } finally {
            connection.disconnect();
        }
        if (status < 200 || status >= 300) {
            throw new IOException("GitHub update check failed with HTTP " + status + ".");
        }
        return parseUpdate(new JSONArray(raw), currentVersionCode);
    }

    static UpdateInfo parseUpdate(JSONArray releases, int currentVersionCode) {
        UpdateInfo best = null;
        for (int releaseIndex = 0; releaseIndex < releases.length(); releaseIndex++) {
            JSONObject release = releases.optJSONObject(releaseIndex);
            if (release == null || release.optBoolean("draft", false)) {
                continue;
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                continue;
            }
            for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                JSONObject asset = assets.optJSONObject(assetIndex);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                Matcher nameMatch = APK_NAME.matcher(name);
                Matcher digestMatch = SHA256.matcher(asset.optString("digest", ""));
                if (!nameMatch.matches() || !digestMatch.matches()) {
                    continue;
                }
                int versionCode;
                try {
                    versionCode = Integer.parseInt(nameMatch.group(1));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                String url = asset.optString("browser_download_url", "");
                if (versionCode <= currentVersionCode || !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
                    continue;
                }
                UpdateInfo candidate = new UpdateInfo(
                    versionCode,
                    nameMatch.group(2),
                    name,
                    url,
                    digestMatch.group(1).toLowerCase(Locale.ROOT),
                    asset.optLong("size", -1)
                );
                if (best == null || candidate.versionCode > best.versionCode) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder raw = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }
        }
        return raw.toString();
    }

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String assetName;
        public final String downloadUrl;
        public final String sha256;
        public final long size;

        UpdateInfo(int versionCode, String versionName, String assetName, String downloadUrl, String sha256, long size) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.size = size;
        }
    }
}
