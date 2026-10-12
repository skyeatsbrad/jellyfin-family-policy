package org.familypolicy.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class GitHubUpdateClientTest {
    @Test
    public void selectsHighestVerifiedApkVersion() throws Exception {
        JSONArray releases = new JSONArray()
            .put(release(false, asset(
                "family-policy-android-v2-0.2.0-preview.1.apk",
                "https://example.invalid/v2.apk",
                "sha256:" + "a".repeat(64),
                200
            )))
            .put(release(false, asset(
                "family-policy-android-v4-0.4.0-preview.1.apk",
                "https://example.invalid/v4.apk",
                "sha256:" + "b".repeat(64),
                400
            )))
            .put(release(true, asset(
                "family-policy-android-v5-draft.apk",
                "https://example.invalid/v5.apk",
                "sha256:" + "c".repeat(64),
                500
            )));

        GitHubUpdateClient.UpdateInfo update = GitHubUpdateClient.parseUpdate(releases, 1);
        assertEquals(4, update.versionCode);
        assertEquals("0.4.0-preview.1", update.versionName);
        assertEquals("b".repeat(64), update.sha256);
    }

    @Test
    public void ignoresUnverifiedAndCurrentAssets() throws Exception {
        JSONArray releases = new JSONArray()
            .put(release(false, asset(
                "family-policy-android-v1-current.apk",
                "https://example.invalid/v1.apk",
                "sha256:" + "a".repeat(64),
                100
            )))
            .put(release(false, asset(
                "family-policy-android-v2-missing-digest.apk",
                "https://example.invalid/v2.apk",
                "",
                200
            )));
        assertNull(GitHubUpdateClient.parseUpdate(releases, 1));
    }

    private static JSONObject release(boolean draft, JSONObject asset) throws Exception {
        return new JSONObject()
            .put("draft", draft)
            .put("assets", new JSONArray().put(asset));
    }

    private static JSONObject asset(String name, String url, String digest, long size) throws Exception {
        return new JSONObject()
            .put("name", name)
            .put("browser_download_url", url)
            .put("digest", digest)
            .put("size", size);
    }
}
