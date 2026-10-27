// Sondagem temporária executada como shell por app_process; não faz parte do Home.
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FmtvMediaSessionProbe {
    static MediaSessionManager manager;
    static final Map<MediaSession.Token, String> ids = new HashMap<>();

    static List<MediaController> sessions() { return manager.getActiveSessions(null); }
    static String id(MediaController c) {
        String value = ids.get(c.getSessionToken());
        if (value == null) { value = UUID.randomUUID().toString(); ids.put(c.getSessionToken(), value); }
        return value;
    }
    static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
    static String title(MediaMetadata meta) {
        if (meta == null) return null;
        String value = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence fallback = meta.getDescription().getTitle();
        return value != null ? value : fallback == null ? null : fallback.toString();
    }
    static String item(MediaController c) throws Exception {
        MediaMetadata m = c.getMetadata();
        String raw = String.valueOf(title(m)) + "|" + (m == null ? "" : m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) out.append(String.format("%02x", hash[i]));
        return out.toString();
    }
    static Bitmap art(MediaMetadata m) {
        if (m == null) return null;
        Bitmap largest = null;
        for (String key : new String[] {MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap value = m.getBitmap(key);
            if (value != null && (largest == null || value.getWidth() * (long)value.getHeight() > largest.getWidth() * (long)largest.getHeight())) largest = value;
        }
        Bitmap icon = m.getDescription().getIconBitmap();
        return largest != null ? largest : icon;
    }
    static JSONObject read(MediaController c) throws Exception {
        MediaMetadata m = c.getMetadata();
        PlaybackState p = c.getPlaybackState();
        MediaDescription d = m == null ? null : m.getDescription();
        Bitmap bitmap = art(m);
        boolean uri = false;
        if (m != null) for (String key : new String[] {MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) uri |= m.getString(key) != null;
        uri |= d != null && d.getIconUri() != null;
        long actions = p == null ? 0 : p.getActions();
        JSONObject caps = new JSONObject().put("play", (actions & PlaybackState.ACTION_PLAY) != 0).put("pause", (actions & PlaybackState.ACTION_PAUSE) != 0)
            .put("seek", (actions & PlaybackState.ACTION_SEEK_TO) != 0).put("next", (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0).put("previous", (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        long rawPosition = p == null ? -1 : p.getPosition();
        long position = rawPosition;
        if (p != null && p.getState() == PlaybackState.STATE_PLAYING && position >= 0 && p.getLastPositionUpdateTime() > 0)
            position += (long)((SystemClock.elapsedRealtime() - p.getLastPositionUpdateTime()) * p.getPlaybackSpeed());
        return new JSONObject().put("id", id(c)).put("item", item(c)).put("package", c.getPackageName()).put("title", nullable(title(m)))
            .put("subtitle", nullable(d == null ? null : d.getSubtitle())).put("mediaIdPresent", m != null && m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) != null)
            .put("metadataKeys", m == null ? new JSONArray() : new JSONArray(m.keySet())).put("durationMillis", m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION))
            .put("state", p == null ? -1 : p.getState()).put("rawPositionMillis", rawPosition).put("positionMillis", position)
            .put("speed", p == null ? 0 : p.getPlaybackSpeed()).put("actions", actions).put("capabilities", caps)
            .put("artBitmap", bitmap == null ? JSONObject.NULL : new JSONObject().put("width", bitmap.getWidth()).put("height", bitmap.getHeight()))
            .put("artUriPresent", uri);
    }
    static MediaController target(JSONObject request) throws Exception {
        for (MediaController c : sessions()) if (id(c).equals(request.getString("target"))) {
            if (!item(c).equals(request.getString("item"))) throw new IllegalStateException("item-changed");
            return c;
        }
        throw new IllegalStateException("session-ended");
    }
    static JSONObject execute(JSONObject request) throws Exception {
        String action = request.getString("action");
        if (action.equals("snapshot")) {
            JSONArray values = new JSONArray();
            for (MediaController c : sessions()) values.put(read(c));
            return new JSONObject().put("sessions", values);
        }
        MediaController c = target(request);
        if (action.equals("art")) {
            Bitmap value = art(c.getMetadata());
            if (value == null) return new JSONObject().put("available", false);
            float scale = Math.min(1f, 512f / Math.max(value.getWidth(), value.getHeight()));
            Bitmap scaled = Bitmap.createScaledBitmap(value, Math.max(1, (int)(value.getWidth() * scale)), Math.max(1, (int)(value.getHeight() * scale)), true);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, bytes);
            return new JSONObject().put("available", true).put("jpeg", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
        }
        if (action.equals("forward") || action.equals("backward")) {
            PlaybackState before = c.getPlaybackState();
            long bit = action.equals("forward") ? PlaybackState.ACTION_FAST_FORWARD : PlaybackState.ACTION_REWIND;
            if (before == null || (before.getActions() & bit) == 0) throw new IllegalStateException("not-advertised");
            JSONObject initial = read(c);
            long position = initial.getLong("positionMillis") + (action.equals("forward") ? 10000 : -10000);
            long started = SystemClock.elapsedRealtime();
            if (request.optBoolean("mediaButton")) {
                int key = action.equals("forward") ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD : KeyEvent.KEYCODE_MEDIA_REWIND;
                boolean down = c.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                boolean up = c.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                initial.put("mediaButtonAccepted", down && up);
            } else if (action.equals("forward")) c.getTransportControls().fastForward(); else c.getTransportControls().rewind();
            boolean matched = false;
            while (SystemClock.elapsedRealtime() - started <= 5000) {
                target(request);
                if (Math.abs(read(c).getLong("positionMillis") - position) <= 2500) { matched = true; break; }
                Thread.sleep(100);
            }
            return new JSONObject().put("action", action).put("before", initial).put("after", read(c)).put("targetMillis", position)
                .put("matched", matched).put("elapsedMillis", SystemClock.elapsedRealtime() - started).put("dispatched", true);
        }
        if (action.equals("seek")) {
            PlaybackState before = c.getPlaybackState();
            if (before == null || (before.getActions() & PlaybackState.ACTION_SEEK_TO) == 0) throw new IllegalStateException("not-advertised");
            JSONObject initial = read(c);
            long position = request.has("positionMillis") ? request.getLong("positionMillis") : initial.getLong("positionMillis") + request.getLong("offsetMillis");
            position = Math.max(0, position);
            long duration = initial.optLong("durationMillis");
            if (duration > 0) position = Math.min(position, duration);
            long started = SystemClock.elapsedRealtime();
            c.getTransportControls().seekTo(position);
            boolean matched = false;
            while (SystemClock.elapsedRealtime() - started <= 5000) {
                target(request);
                if (Math.abs(read(c).getLong("positionMillis") - position) <= 2500) { matched = true; break; }
                Thread.sleep(100);
            }
            return new JSONObject().put("action", action).put("before", initial).put("after", read(c)).put("targetMillis", position)
                .put("matched", matched).put("elapsedMillis", SystemClock.elapsedRealtime() - started).put("dispatched", true);
        }
        if (!action.equals("play") && !action.equals("pause")) throw new IllegalArgumentException("unsupported-action");
        PlaybackState before = c.getPlaybackState();
        int expected = action.equals("play") ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        JSONObject initial = read(c);
        if (before != null && before.getState() == expected)
            return new JSONObject().put("action", action).put("before", initial).put("after", initial)
                .put("matched", true).put("elapsedMillis", 0).put("dispatched", false);
        long bit = action.equals("play") ? PlaybackState.ACTION_PLAY : PlaybackState.ACTION_PAUSE;
        if (before == null || (before.getActions() & bit) == 0) throw new IllegalStateException("not-advertised");
        long started = SystemClock.elapsedRealtime();
        if (action.equals("play")) c.getTransportControls().play(); else c.getTransportControls().pause();
        boolean matched = false;
        while (SystemClock.elapsedRealtime() - started <= 5000) {
            target(request); // Recusa destruição/troca de obra durante a observação.
            PlaybackState current = c.getPlaybackState();
            if (current != null && current.getState() == expected) { matched = true; break; }
            Thread.sleep(100);
        }
        return new JSONObject().put("action", action).put("before", initial).put("after", read(c)).put("matched", matched)
            .put("elapsedMillis", SystemClock.elapsedRealtime() - started).put("dispatched", true);
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        new Thread(() -> {
            try { serve(type, thread); }
            catch (Exception error) { error.printStackTrace(); System.exit(1); }
        }, "probe-input").start();
        Looper.loop();
    }
    static void serve(Class<?> type, Object thread) throws Exception {
        Context context = (Context)type.getMethod("getSystemContext").invoke(thread);
        // app_process não passa pela inicialização de mídia do Zygote no Android 12.
        Class<?> mediaManager = Class.forName("android.media.MediaServiceManager");
        Object serviceManager = mediaManager.getConstructor().newInstance();
        for (String initializer : new String[] {"android.media.MediaFrameworkPlatformInitializer", "android.media.MediaFrameworkInitializer"})
            Class.forName(initializer).getMethod("setMediaServiceManager", mediaManager).invoke(null, serviceManager);
        manager = (MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = input.readLine()) != null) {
            if (line.length() > 4096) break;
            JSONObject output = new JSONObject().put("observedAtMillis", System.currentTimeMillis()).put("uid", android.os.Process.myUid());
            try { output.put("result", execute(new JSONObject(line))); }
            catch (Exception error) { output.put("error", String.valueOf(error.getMessage())); }
            System.out.println(output.toString());
            System.out.flush();
        }
        System.exit(0);
    }
}
