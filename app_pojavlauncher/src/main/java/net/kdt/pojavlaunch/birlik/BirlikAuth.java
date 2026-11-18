package net.kdt.pojavlaunch.birlik;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * BirlikCraft account login through movarus.info/launcher_auth.php.
 *
 * The launcher only works with a BirlikCraft account: Microsoft and offline logins are removed,
 * the game cannot be launched without a session, and the password is never stored on the device.
 */
public final class BirlikAuth {
    private static final String TAG = "BirlikAuth";
    private static final String AUTH_URL = "https://movarus.info/launcher_auth.php";
    private static final String USER_AGENT = "BirlikCraftLauncher/1.0";
    private static final int TIMEOUT_MS = 15000;

    private static final String PREFS = "birlik_auth";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_CREDIT = "credit";
    private static final String KEY_LOGIN_TIME = "login_time";

    private BirlikAuth() {}

    /** A logged in BirlikCraft player. */
    public static final class Session {
        public final String username;
        public final String uuid;
        public final String credit;

        Session(String username, String uuid, String credit) {
            this.username = username;
            this.uuid = uuid;
            this.credit = credit;
        }
    }

    /** Login refused by the server (wrong password, banned account, clock out of sync...). */
    public static final class AuthException extends Exception {
        public AuthException(String message) { super(message); }
    }

    // ------------------------------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    public static Session getSession(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        String username = prefs.getString(KEY_USERNAME, null);
        String uuid = prefs.getString(KEY_UUID, null);
        if (username == null || uuid == null) return null;
        return new Session(username, uuid, prefs.getString(KEY_CREDIT, "0"));
    }

    /**
     * @return true when a BirlikCraft session exists and the launcher's selected game account
     * is that session's account.
     */
    public static boolean isLoggedIn(@NonNull Context context) {
        Session session = getSession(context);
        if (session == null) return false;
        if (!new File(Tools.DIR_ACCOUNT_NEW, session.username + ".json").isFile()) return false;
        String current = PojavProfile.getCurrentProfileName(context);
        return session.username.equals(current);
    }

    /** Store the session and make its game account the only launcher account. */
    public static void saveSession(@NonNull Context context, @NonNull Session session) throws IOException {
        deleteAllGameAccounts();
        net.kdt.pojavlaunch.utils.FileUtils.ensureDirectory(new File(Tools.DIR_ACCOUNT_NEW));
        MinecraftAccount account = new MinecraftAccount();
        account.username = session.username;
        account.profileId = session.uuid;
        account.accessToken = "0";   // offline-mode game session
        account.isMicrosoft = false;
        account.save();
        PojavProfile.setCurrentProfile(context, session.username);

        prefs(context).edit()
                .putString(KEY_USERNAME, session.username)
                .putString(KEY_UUID, session.uuid)
                .putString(KEY_CREDIT, session.credit)
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void logout(@NonNull Context context) {
        prefs(context).edit().clear().apply();
        deleteAllGameAccounts();
        PojavProfile.setCurrentProfile(context, null);
    }

    private static void deleteAllGameAccounts() {
        File[] accounts = new File(Tools.DIR_ACCOUNT_NEW).listFiles();
        if (accounts == null) return;
        for (File account : accounts) {
            if (account.isFile() && !account.delete()) Log.w(TAG, "Could not delete " + account);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Network login (blocking, call from a background thread)
    // ------------------------------------------------------------------------------------------

    @NonNull
    public static Session login(@NonNull String username, @NonNull String password)
            throws AuthException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("username", username.trim());
        request.addProperty("password", password);
        String body = request.toString();
        long timestamp = System.currentTimeMillis() / 1000L;

        HttpURLConnection connection = (HttpURLConnection) new URL(AUTH_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("X-Launcher-Timestamp", Long.toString(timestamp));
        connection.setRequestProperty("X-Launcher-Signature", sign(body + ":" + timestamp));

        String responseText;
        int code;
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            code = connection.getResponseCode();
            InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            responseText = in == null ? "" : readFully(in);
        } catch (IOException e) {
            // DNS, TLS, timeout or refused connection: the server was not reached
            Log.w(TAG, "Login request failed", e);
            throw new IOException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        } finally {
            connection.disconnect();
        }

        // The server answered: anything that is not our JSON is reported with its HTTP code so
        // problems like a wrong URL (404), a firewall/Cloudflare page (403) or a PHP error (500)
        // can be told apart from a network problem.
        JsonObject response = null;
        try {
            response = Tools.GLOBAL_GSON.fromJson(responseText, JsonObject.class);
        } catch (RuntimeException ignored) {}
        if (response == null) {
            Log.w(TAG, "Unexpected login answer (HTTP " + code + "): " + responseText);
            throw new AuthException("Sunucu beklenmeyen bir yanıt verdi (HTTP " + code + "): " + preview(responseText));
        }

        JsonElement success = response.get("success");
        if (success == null || !success.isJsonPrimitive() || !success.getAsBoolean()) {
            String error = getString(response, "error");
            throw new AuthException(error != null ? error : "Giriş başarısız");
        }

        String name = getString(response, "username");
        if (name == null) name = username.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new AuthException("Minecraft kullanıcı adınız 3-16 karakter olmalı ve sadece harf, rakam ve _ içermeli: " + name);
        }
        String credit = getString(response, "credit");
        return new Session(name, toUuid(getString(response, "uuid"), name), credit == null ? "0" : credit);
    }

    private static String sign(String data) throws IOException {
        String secret = BuildConfig.BIRLIK_AUTH_SECRET;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Could not sign the login request", e);
        }
    }

    /** Use the server's UUID (with or without dashes), else the standard offline-mode UUID. */
    private static String toUuid(@Nullable String raw, String username) {
        if (raw != null) {
            String hex = raw.replace("-", "").toLowerCase(Locale.ROOT);
            if (hex.matches("[0-9a-f]{32}")) {
                return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                        + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            }
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Nullable
    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        String value = element.getAsString();
        return value.isEmpty() ? null : value;
    }

    /** First characters of a server answer, without HTML tags, for error messages. */
    private static String preview(String text) {
        String plain = text.replaceAll("(?s)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) return "boş yanıt";
        return plain.length() > 120 ? plain.substring(0, 120) + "…" : plain;
    }

    private static String readFully(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
