package org.familypolicy.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class JellyfinClient {
    public static final String VERSION = "0.1.0-preview.1";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    private final String baseUrl;
    private final String deviceId;
    private final String token;
    private final String userId;

    public JellyfinClient(String baseUrl, String deviceId, String token, String userId) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.deviceId = deviceId;
        this.token = token;
        this.userId = userId;
    }

    public static Session authenticate(String baseUrl, String username, String password, String deviceId)
        throws IOException, JSONException {
        JellyfinClient anonymous = new JellyfinClient(baseUrl, deviceId, null, null);
        JSONObject body = new JSONObject()
            .put("Username", username)
            .put("Pw", password);
        JSONObject response = anonymous.requestObject("POST", "/Users/AuthenticateByName", body);
        JSONObject user = response.getJSONObject("User");
        return new Session(
            anonymous.baseUrl,
            response.getString("AccessToken"),
            user.getString("Id"),
            user.optString("Name", username),
            user.optJSONObject("Policy") != null && user.getJSONObject("Policy").optBoolean("IsAdministrator", false)
        );
    }

    public JSONObject health() throws IOException, JSONException {
        return requestObject("GET", "/FamilyPolicy/Health", null);
    }

    public JSONObject state() throws IOException, JSONException {
        return requestObject("GET", "/FamilyPolicy/State", null);
    }

    public JSONArray users() throws IOException, JSONException {
        Object response = request("GET", "/Users", null);
        if (!(response instanceof JSONArray)) {
            throw new IOException("Jellyfin returned an invalid users response.");
        }
        return (JSONArray) response;
    }

    public JSONObject items(String includeTypes, String search, int startIndex, int limit)
        throws IOException, JSONException {
        StringBuilder path = new StringBuilder("/Items?Recursive=true")
            .append("&UserId=").append(encode(userId))
            .append("&IncludeItemTypes=").append(encode(includeTypes))
            .append("&StartIndex=").append(startIndex)
            .append("&Limit=").append(limit)
            .append("&SortBy=SortName&SortOrder=Ascending")
            .append("&Fields=ProductionYear,SeriesName");
        if (search != null && !search.trim().isEmpty()) {
            path.append("&SearchTerm=").append(encode(search.trim()));
        }
        return requestObject("GET", path.toString(), null);
    }

    public JSONObject updateAccounts(long expectedRevision, Map<String, JSONObject> policies)
        throws IOException, JSONException {
        JSONArray accounts = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : policies.entrySet()) {
            accounts.put(new JSONObject()
                .put("UserId", entry.getKey())
                .put("Policy", entry.getValue()));
        }
        JSONObject body = new JSONObject()
            .put("ExpectedRevision", expectedRevision)
            .put("Accounts", accounts);
        return requestObject("PUT", "/FamilyPolicy/Accounts", body);
    }

    public JSONObject restoreAccount(long expectedRevision, String accountId) throws IOException, JSONException {
        JSONObject body = new JSONObject().put("ExpectedRevision", expectedRevision);
        return requestObject("POST", "/FamilyPolicy/Accounts/" + encodePath(accountId) + "/Restore", body);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String userId() {
        return userId;
    }

    public static String normalizeBaseUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Use an http:// or https:// Jellyfin server URL.");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Enter the Jellyfin server base URL without credentials, query, or fragment.");
            }
            return value;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Enter a valid Jellyfin server URL.", ex);
        }
    }

    public static boolean isCleartext(String baseUrl) {
        return baseUrl != null && baseUrl.trim().toLowerCase(Locale.ROOT).startsWith("http://");
    }

    private JSONObject requestObject(String method, String path, JSONObject body) throws IOException, JSONException {
        Object response = request(method, path, body);
        if (!(response instanceof JSONObject)) {
            throw new IOException("Jellyfin returned an invalid response.");
        }
        return (JSONObject) response;
    }

    private Object request(String method, String path, JSONObject body) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", authorizationHeader());
            connection.setRequestProperty("X-Emby-Authorization", authorizationHeader());
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("X-Emby-Token", token);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            String raw = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                throw new HttpException(status, raw);
            }
            if (raw.isEmpty()) {
                return null;
            }
            String trimmed = raw.trim();
            return trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
        } finally {
            connection.disconnect();
        }
    }

    private String authorizationHeader() {
        StringBuilder header = new StringBuilder("MediaBrowser Client=\"Family Policy Android\", Device=\"Android\", DeviceId=\"")
            .append(escapeHeader(deviceId))
            .append("\", Version=\"")
            .append(VERSION)
            .append("\"");
        if (token != null && !token.isEmpty()) {
            header.append(", Token=\"").append(escapeHeader(token)).append("\"");
        }
        return header.toString();
    }

    private static String escapeHeader(String value) {
        return value == null ? "" : value.replace("\\", "").replace("\"", "");
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private static String encodePath(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9-]", "");
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        return output.toString();
    }

    public static final class Session {
        public final String serverUrl;
        public final String token;
        public final String userId;
        public final String userName;
        public final boolean administrator;

        public Session(String serverUrl, String token, String userId, String userName, boolean administrator) {
            this.serverUrl = serverUrl;
            this.token = token;
            this.userId = userId;
            this.userName = userName;
            this.administrator = administrator;
        }
    }

    public static final class HttpException extends IOException {
        public final int status;
        public final String responseBody;

        HttpException(int status, String responseBody) {
            super("HTTP " + status);
            this.status = status;
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        public String serverMessage() {
            try {
                String message = new JSONObject(responseBody).optString("error", "");
                return message.isEmpty() ? getMessage() : message;
            } catch (JSONException ignored) {
                return getMessage();
            }
        }
    }
}
