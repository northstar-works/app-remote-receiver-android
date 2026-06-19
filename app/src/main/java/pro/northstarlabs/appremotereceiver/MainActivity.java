package pro.northstarlabs.appremotereceiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PORT = 8765;
    private static final int MAX_UPLOAD_BYTES = 300 * 1024 * 1024;
    private static final String ACTION_INSTALL_RESULT = "pro.northstarlabs.appremotereceiver.INSTALL_RESULT";

    private TextView statusView;
    private TextView ipView;
    private TextView tokenView;
    private TextView lastApkView;
    private ReceiverServer server;
    private File lastApk;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getOrCreateToken();
        buildUi();
        startServer();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(11, 16, 28));
        scroll.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("app_icon_512", "drawable", getPackageName()));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(220, 220);
        logo.setLayoutParams(logoParams);
        root.addView(logo);

        TextView title = tv("App Remote Receiver", 34, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusView = tv("Starting receiver...", 22, Color.rgb(0, 230, 210), false);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView);

        ipView = tv("IP: " + getDeviceIpText() + "  Port: " + PORT, 24, Color.rgb(245, 247, 251), false);
        ipView.setGravity(Gravity.CENTER);
        root.addView(ipView);

        tokenView = tv("Pairing token: " + token, 28, Color.rgb(255, 154, 26), true);
        tokenView.setGravity(Gravity.CENTER);
        root.addView(tokenView);

        TextView help = tv("Keep this screen open while sending an APK. After an APK is received, choose Install and approve the normal Fire TV installer screen.", 18, Color.rgb(169, 180, 198), false);
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, 20, 0, 20);
        root.addView(help);

        lastApkView = tv("No APK received yet.", 20, Color.rgb(169, 180, 198), false);
        lastApkView.setGravity(Gravity.CENTER);
        root.addView(lastApkView);

        Button installBtn = button("Install Last APK");
        installBtn.setOnClickListener(v -> {
            if (lastApk != null && lastApk.exists()) {
                installApk(lastApk);
            } else {
                Toast.makeText(this, "No APK received yet", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(installBtn);

        Button permBtn = button("Open Install Permission Settings");
        permBtn.setOnClickListener(v -> openUnknownSourcesSettings());
        root.addView(permBtn);

        Button refreshBtn = button("Refresh IP Display");
        refreshBtn.setOnClickListener(v -> ipView.setText("IP: " + getDeviceIpText() + "  Port: " + PORT));
        root.addView(refreshBtn);

        setContentView(scroll);
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, 18, 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private String getOrCreateToken() {
        SharedPreferences prefs = getSharedPreferences("receiver", MODE_PRIVATE);
        String existing = prefs.getString("token", null);
        if (existing != null) return existing;
        SecureRandom r = new SecureRandom();
        String created = String.format(Locale.US, "%06d", r.nextInt(1000000));
        prefs.edit().putString("token", created).apply();
        return created;
    }

    private void startServer() {
        try {
            server = new ReceiverServer(this, PORT, token, this::onApkReceived);
            server.start();
            statusView.setText("Ready to receive");
        } catch (IOException e) {
            statusView.setText("Server failed: " + e.getMessage());
        }
    }

    private void onApkReceived(File apk) {
        runOnUiThread(() -> {
            lastApk = apk;
            long mb = Math.max(1, apk.length() / 1024 / 1024);
            lastApkView.setText("Received: " + apk.getName() + " (" + mb + " MB)");
            new AlertDialog.Builder(this)
                    .setTitle("APK received")
                    .setMessage("Install " + apk.getName() + " now?")
                    .setPositiveButton("Install", (d, w) -> installApk(apk))
                    .setNegativeButton("Later", null)
                    .show();
        });
    }

    private void openUnknownSourcesSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                Intent i = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(i);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installApk(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Enable install permission for App Remote Receiver, then press Install again.", Toast.LENGTH_LONG).show();
            openUnknownSourcesSettings();
            return;
        }

        PackageInstaller.Session session = null;
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(null);
            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            }

            Intent callback = new Intent(this, InstallResultReceiver.class);
            callback.setAction(ACTION_INSTALL_RESULT);
            callback.putExtra("apk_name", apk.getName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, sessionId, callback, flags);
            session.commit(pendingIntent.getIntentSender());
            Toast.makeText(this, "Installer opened. Approve on screen if prompted.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Install failed to start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) { }
            }
        }
    }

    private String getDeviceIpText() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            StringBuilder sb = new StringBuilder();
            for (NetworkInterface ni : interfaces) {
                for (java.net.InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(addr.getHostAddress());
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    public static class InstallResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmation);
                } else {
                    Toast.makeText(context, "Installer needs approval, but no confirmation screen was returned.", Toast.LENGTH_LONG).show();
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(context, "Install complete", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Install failed: " + safe(message), Toast.LENGTH_LONG).show();
            }
        }
    }

    interface ApkListener { void received(File apk); }

    static class ReceiverServer {
        private final Context context;
        private final int port;
        private final String token;
        private final ApkListener listener;
        private ServerSocket serverSocket;
        private volatile boolean running;

        ReceiverServer(Context context, int port, String token, ApkListener listener) {
            this.context = context.getApplicationContext();
            this.port = port;
            this.token = token;
            this.listener = listener;
        }

        void start() throws IOException {
            serverSocket = new ServerSocket(port);
            running = true;
            Thread thread = new Thread(this::loop, "AppRemoteReceiverServer");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException ignored) { }
            }
        }

        private void loop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread t = new Thread(() -> handle(socket), "AppRemoteReceiverClient");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        private void handle(Socket socket) {
            try (Socket s = socket) {
                s.setSoTimeout(300000);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();

                byte[] headerBytes = readHeaders(in);
                if (headerBytes.length == 0) {
                    respond(out, 400, "application/json", "{\"ok\":false,\"error\":\"empty request\"}");
                    return;
                }

                String headersText = new String(headerBytes, StandardCharsets.ISO_8859_1);
                String[] lines = headersText.split("\\r?\\n");
                if (lines.length == 0) {
                    respond(out, 400, "application/json", "{\"ok\":false,\"error\":\"bad request\"}");
                    return;
                }

                String[] first = lines[0].split(" ");
                String method = first.length > 0 ? first[0] : "";
                String target = first.length > 1 ? first[1] : "/";
                Map<String, String> headers = parseHeaders(lines);

                if ("GET".equalsIgnoreCase(method)) {
                    respond(out, 200, "application/json", "{\"ok\":true,\"app\":\"App Remote Receiver\",\"port\":8765}");
                    return;
                }

                if (!"POST".equalsIgnoreCase(method) || !target.startsWith("/upload")) {
                    respond(out, 404, "application/json", "{\"ok\":false,\"error\":\"not found\"}");
                    return;
                }

                String supplied = getQueryParam(target, "token");
                if (supplied == null || !supplied.equals(token)) {
                    respond(out, 401, "application/json", "{\"ok\":false,\"error\":\"bad token\"}");
                    return;
                }

                int len = parseInt(headers.get("content-length"), -1);
                if (len < 1 || len > MAX_UPLOAD_BYTES) {
                    respond(out, 413, "application/json", "{\"ok\":false,\"error\":\"missing or too large Content-Length\"}");
                    return;
                }

                byte[] body = readExact(in, len);
                String contentType = headers.get("content-type");
                byte[] apkBytes = extractApkBytes(body, contentType);
                if (apkBytes.length < 4 || apkBytes[0] != 'P' || apkBytes[1] != 'K') {
                    respond(out, 400, "application/json", "{\"ok\":false,\"error\":\"uploaded file does not look like an APK\"}");
                    return;
                }

                File incomingDir = new File(context.getCacheDir(), "incoming");
                if (!incomingDir.exists()) incomingDir.mkdirs();
                File apk = new File(incomingDir, "received_" + System.currentTimeMillis() + ".apk");
                try (FileOutputStream fos = new FileOutputStream(apk)) {
                    fos.write(apkBytes);
                }
                listener.received(apk);
                respond(out, 200, "application/json", "{\"ok\":true,\"message\":\"APK received. Check TV screen to install.\"}");
            } catch (SocketTimeoutException e) {
                // Client stalled; socket will close.
            } catch (Exception e) {
                try {
                    OutputStream out = socket.getOutputStream();
                    respond(out, 500, "application/json", "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
                } catch (Exception ignored) { }
            }
        }

        private byte[] readHeaders(InputStream in) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int b;
            int state = 0;
            while ((b = in.read()) != -1) {
                baos.write(b);
                if (state == 0 && b == '\r') state = 1;
                else if (state == 1 && b == '\n') state = 2;
                else if (state == 2 && b == '\r') state = 3;
                else if (state == 3 && b == '\n') break;
                else state = 0;
                if (baos.size() > 64 * 1024) break;
            }
            return baos.toByteArray();
        }

        private Map<String, String> parseHeaders(String[] lines) {
            Map<String, String> map = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int idx = lines[i].indexOf(':');
                if (idx > 0) {
                    String k = lines[i].substring(0, idx).trim().toLowerCase(Locale.US);
                    String v = lines[i].substring(idx + 1).trim();
                    map.put(k, v);
                }
            }
            return map;
        }

        private byte[] readExact(InputStream in, int len) throws IOException {
            byte[] data = new byte[len];
            int off = 0;
            while (off < len) {
                int n = in.read(data, off, len - off);
                if (n < 0) throw new IOException("unexpected end of upload");
                off += n;
            }
            return data;
        }

        private byte[] extractApkBytes(byte[] body, String contentType) throws IOException {
            if (contentType == null || !contentType.toLowerCase(Locale.US).contains("multipart/form-data")) {
                return body;
            }
            String boundary = null;
            for (String part : contentType.split(";")) {
                String p = part.trim();
                if (p.startsWith("boundary=")) {
                    boundary = p.substring("boundary=".length());
                    if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 1) {
                        boundary = boundary.substring(1, boundary.length() - 1);
                    }
                    break;
                }
            }
            if (boundary == null) throw new IOException("multipart boundary missing");

            byte[] headerEnd = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
            int start = indexOf(body, headerEnd, 0);
            if (start < 0) throw new IOException("multipart part header missing");
            int fileStart = start + headerEnd.length;
            byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            int fileEnd = indexOf(body, boundaryBytes, fileStart);
            if (fileEnd < 0) fileEnd = body.length;
            byte[] file = new byte[fileEnd - fileStart];
            System.arraycopy(body, fileStart, file, 0, file.length);
            return file;
        }

        private int indexOf(byte[] data, byte[] pattern, int start) {
            outer:
            for (int i = Math.max(0, start); i <= data.length - pattern.length; i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) continue outer;
                }
                return i;
            }
            return -1;
        }

        private String getQueryParam(String target, String name) {
            int q = target.indexOf('?');
            if (q < 0 || q + 1 >= target.length()) return null;
            String query = target.substring(q + 1);
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String k = eq >= 0 ? pair.substring(0, eq) : pair;
                String v = eq >= 0 ? pair.substring(eq + 1) : "";
                if (name.equals(k)) {
                    try {
                        return URLDecoder.decode(v, "UTF-8");
                    } catch (Exception e) {
                        return v;
                    }
                }
            }
            return null;
        }

        private int parseInt(String value, int fallback) {
            try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
        }

        private void respond(OutputStream out, int code, String type, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large" : "Internal Server Error";
            String head = "HTTP/1.1 " + code + " " + status + "\r\n" +
                    "Content-Type: " + type + "; charset=utf-8\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.flush();
        }
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "unknown";
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }
}
