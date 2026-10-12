package org.familypolicy.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class JellyfinClientTest {
    private ServerSocket server;
    private String baseUrl;

    @Before
    public void startServer() throws Exception {
        server = new ServerSocket(0);
        baseUrl = "http://127.0.0.1:" + server.getLocalPort();
    }

    @After
    public void stopServer() throws Exception {
        server.close();
    }

    @Test
    public void authenticatesWithJellyfinClientHeader() throws Exception {
        AtomicReference<Request> request = new AtomicReference<>();
        serveOnce(received -> {
            request.set(received);
            return new Response(200, new JSONObject()
                .put("AccessToken", "token-value")
                .put("User", new JSONObject()
                    .put("Id", "admin-id")
                    .put("Name", "Parent")
                    .put("Policy", new JSONObject().put("IsAdministrator", true)))
                .toString());
        });

        JellyfinClient.Session session =
            JellyfinClient.authenticate(baseUrl, "Parent", "secret", "device-id");

        assertEquals("token-value", session.token);
        assertTrue(session.administrator);
        assertEquals("/Users/AuthenticateByName", request.get().path);
        assertTrue(request.get().authorization.contains("Client=\"Family Policy Android\""));
        assertTrue(request.get().body.contains("\"Username\":\"Parent\""));
        assertTrue(request.get().body.contains("\"Pw\":\"secret\""));
    }

    @Test
    public void bulkUpdateUsesAuthenticatedAtomicEndpoint() throws Exception {
        AtomicReference<Request> request = new AtomicReference<>();
        serveOnce(received -> {
            request.set(received);
            return new Response(200, "{\"Revision\":8}");
        });
        JellyfinClient client = new JellyfinClient(baseUrl, "device", "token", "admin");
        LinkedHashMap<String, JSONObject> policies = new LinkedHashMap<>();
        policies.put("child-id", PolicyJson.defaultPolicy());

        JSONObject result = client.updateAccounts(7, policies);

        JSONObject body = new JSONObject(request.get().body);
        assertEquals(8, result.getInt("Revision"));
        assertEquals("/FamilyPolicy/Accounts", request.get().path);
        assertTrue(request.get().authorization.contains("Token=\"token\""));
        assertEquals(7, body.getInt("ExpectedRevision"));
        assertEquals("child-id", body.getJSONArray("Accounts").getJSONObject(0).getString("UserId"));
    }

    @Test
    public void rejectsInvalidServerUrls() {
        assertThrows(IllegalArgumentException.class, () -> JellyfinClient.normalizeBaseUrl("ftp://server"));
        assertThrows(IllegalArgumentException.class, () -> JellyfinClient.normalizeBaseUrl("https://user@example.com"));
        assertEquals("https://example.com/jellyfin", JellyfinClient.normalizeBaseUrl("https://example.com/jellyfin/"));
    }

    @Test
    public void exposesServerErrorMessage() throws Exception {
        serveOnce(received -> new Response(503, "{\"error\":\"Start the health guard.\"}"));
        JellyfinClient client = new JellyfinClient(baseUrl, "device", "token", "admin");
        JellyfinClient.HttpException error =
            assertThrows(JellyfinClient.HttpException.class, client::state);
        assertEquals(503, error.status);
        assertEquals("Start the health guard.", error.serverMessage());
    }

    private void serveOnce(Handler handler) {
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets.UTF_8
                ));
                String requestLine = reader.readLine();
                int contentLength = 0;
                String authorization = "";
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    int separator = line.indexOf(':');
                    if (separator < 0) {
                        continue;
                    }
                    String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(separator + 1).trim();
                    if ("content-length".equals(name)) {
                        contentLength = Integer.parseInt(value);
                    } else if ("authorization".equals(name)) {
                        authorization = value;
                    }
                }
                char[] bodyChars = new char[contentLength];
                int offset = 0;
                while (offset < contentLength) {
                    int read = reader.read(bodyChars, offset, contentLength - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                String[] firstLine = requestLine.split(" ");
                Response response = handler.handle(new Request(
                    firstLine[0],
                    firstLine[1],
                    authorization,
                    new String(bodyChars, 0, offset)
                ));
                byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                output.write(("HTTP/1.1 " + response.status + " Test\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write("Content-Type: application/json\r\n".getBytes(StandardCharsets.US_ASCII));
                output.write(("Content-Length: " + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                output.write(bytes);
                socket.getOutputStream().write(output.toByteArray());
                socket.getOutputStream().flush();
            } catch (Exception ignored) {
                // The client assertion reports protocol failures.
            }
        }, "jellyfin-client-test-server");
        thread.start();
    }

    private interface Handler {
        Response handle(Request request) throws Exception;
    }

    private static final class Request {
        final String method;
        final String path;
        final String authorization;
        final String body;

        Request(String method, String path, String authorization, String body) {
            this.method = method;
            this.path = path;
            this.authorization = authorization;
            this.body = body;
        }
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
