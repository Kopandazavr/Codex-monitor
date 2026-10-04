package dev.kopandazavr.codexmonitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
@SuppressLint({"ApplySharedPref"})
public final class SecureTokenStore {
    private static final String KEY_ALIAS = "codex_monitor_auth_key_v1";
    private static final String KEY_BLOB = "blob";
    private static final Object LOCK = new Object();
    private static final String PREFS = "secure_auth_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private SecureTokenStore() {
    }

    public static void save(Context context, AuthTokens authTokens) throws Exception {
        save(context, AccountContainerStore.selectedId(context), authTokens);
    }

    static void save(Context context, String containerId, AuthTokens authTokens) throws Exception {
        synchronized (LOCK) {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(1, getOrCreateKey());
            byte[] bArrDoFinal = cipher.doFinal(authTokens.toJson().toString().getBytes(StandardCharsets.UTF_8));
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("iv", Base64.getEncoder().encodeToString(cipher.getIV()));
            jSONObject.put("ct", Base64.getEncoder().encodeToString(bArrDoFinal));
            if (!context.getSharedPreferences(PREFS, 0).edit()
                    .putString(blobKey(containerId), jSONObject.toString()).commit()) {
                throw new Exception("Could not persist encrypted credentials.");
            }
        }
    }

    public static AuthTokens load(Context context) {
        return load(context, AccountContainerStore.selectedId(context));
    }

    static AuthTokens load(Context context, String containerId) {
        AuthTokens authTokens = null;
        synchronized (LOCK) {
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS, 0);
            String scopedKey = blobKey(containerId);
            String string = sharedPreferences.getString(scopedKey, null);
            boolean legacy = false;
            if ((string == null || string.isEmpty())
                    && AccountContainerStore.isLegacyOwner(context, containerId)) {
                string = sharedPreferences.getString(KEY_BLOB, null);
                legacy = string != null && !string.isEmpty();
            }
            if (string != null && !string.isEmpty()) {
                try {
                    JSONObject jSONObject = new JSONObject(string);
                    byte[] bArrDecode = Base64.getDecoder().decode(jSONObject.getString("iv"));
                    byte[] bArrDecode2 = Base64.getDecoder().decode(jSONObject.getString("ct"));
                    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                    cipher.init(2, getOrCreateKey(), new GCMParameterSpec(128, bArrDecode));
                    AuthTokens authTokensFromJson = AuthTokens.fromJson(new JSONObject(new String(cipher.doFinal(bArrDecode2), StandardCharsets.UTF_8)));
                    if (!authTokensFromJson.isUsable()) {
                        authTokensFromJson = null;
                    }
                    authTokens = authTokensFromJson;
                    if (legacy && authTokens != null) {
                        sharedPreferences.edit()
                                .putString(scopedKey, string)
                                .remove(KEY_BLOB)
                                .commit();
                    }
                } catch (Exception e) {
                    sharedPreferences.edit().remove(legacy ? KEY_BLOB : scopedKey).commit();
                }
            }
        }
        return authTokens;
    }

    public static boolean isSignedIn(Context context) {
        return isSignedIn(context, AccountContainerStore.selectedId(context));
    }

    static boolean isSignedIn(Context context, String containerId) {
        return load(context, containerId) != null;
    }

    public static void clear(Context context) {
        clear(context, AccountContainerStore.selectedId(context));
    }

    static void clear(Context context, String containerId) {
        synchronized (LOCK) {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, 0)
                    .edit().remove(blobKey(containerId));
            if (AccountContainerStore.isLegacyOwner(context, containerId)) {
                editor.remove(KEY_BLOB);
            }
            editor.commit();
        }
    }

    private static String blobKey(String containerId) {
        String id = containerId == null ? "" : containerId.trim();
        return KEY_BLOB + "::" + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        Key key = keyStore.getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey) {
            return (SecretKey) key;
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, 3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").setRandomizedEncryptionRequired(true).build());
        return keyGenerator.generateKey();
    }
}
