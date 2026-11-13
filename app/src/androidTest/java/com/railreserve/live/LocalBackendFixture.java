package com.railreserve.live;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Exists only in the test APK. Never calls an external provider. */
final class LocalBackendFixture implements AutoCloseable {
    private static boolean keepAwakeRegistered;
    static final String BASE_URL = "http://127.0.0.1:37845";
    static final class Response {
        final int status;
        final String body;
        final long delayMs;
        Response(int status, String body) { this(status, body, 0); }
        Response(int status, String body, long delayMs) {
            this.status = status; this.body = body; this.delayMs = delayMs;
        }
    }
    interface Handler { Response respond(String path); }

    /** Contract fixture only. Values are never described as a live provider verification. */
    static String enquiry(String number,String from,String to,String date,String clazz,boolean fare,Double amount) {
        try {
            org.json.JSONObject data = new org.json.JSONObject().put("trainNumber",number)
                    .put("source",new org.json.JSONObject().put("code",from)).put("destination",new org.json.JSONObject().put("code",to))
                    .put("requestedJourneyDate",date).put("classCode",clazz).put("quotaCode","GN")
                    .put("providerSource",fare ? "RailCore fare estimate" : "RailCore availability")
                    .put("retrievedAt",java.time.Instant.now().toString());
            if (fare) data.put("fareKnown",amount != null).put("totalFare",amount == null ? org.json.JSONObject.NULL : amount);
            else data.put("availabilityDate",date).put("availabilityStatus","GNWL107/WL62").put("statusType","WAITLIST");
            return new org.json.JSONObject().put("success",true).put("data",data).toString();
        } catch (org.json.JSONException failure) { throw new AssertionError(failure); }
    }
    volatile Handler handler;
    final AtomicInteger requests = new AtomicInteger();
    final java.util.concurrent.ConcurrentLinkedQueue<String> paths = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ServerSocket server;
    private final ExecutorService workers = Executors.newCachedThreadPool();

    LocalBackendFixture() throws Exception {
        // Test windows only: keep long timeout checks awake without changing device settings.
        if (!keepAwakeRegistered) {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback((activity, stage) -> {
                    if (stage == androidx.test.runner.lifecycle.Stage.RESUMED)
                        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }));
            keepAwakeRegistered = true;
        }
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 37845), 10);
        workers.execute(() -> {
            while (!server.isClosed()) {
                try { Socket socket = server.accept(); workers.execute(() -> serve(socket)); }
                catch (Exception ignored) { return; }
            }
        });
    }

    private void serve(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String request = reader.readLine();
            if (request == null) return;
            String header;
            do { header = reader.readLine(); } while (header != null && !header.isEmpty());
            Handler selected = handler;
            requests.incrementAndGet();
            String path = request.split(" ")[1];
            paths.add(path);
            Response response = selected.respond(path);
            if (response == null) return; // Simulate a disconnected transport, without toggling phone settings.
            Thread.sleep(response.delayMs);
            byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 " + response.status + " Test\r\nContent-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
            client.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().write(body);
            client.getOutputStream().flush();
        } catch (Exception ignored) { /* Expected when a client times out or the test shuts down. */ }
    }

    @Override public void close() throws Exception {
        server.close();
        workers.shutdownNow();
    }
}
