package org.familypolicy.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureSessionStore {
    private static final String PREFERENCES = "family_policy_session";
    private static final String KEY_ALIAS = "family_policy_android_session";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String SERVER = "server";
    private static final String USER_ID = "user_id";
    private static final String USER_NAME = "user_name";
    private static final String TOKEN = "token";
    private static final String TOKEN_IV = "token_iv";
    private static final String DEVICE_ID = "device_id";

    private final SharedPreferences preferences;

    public SecureSessionStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public String deviceId() {
        String existing = preferences.getString(DEVICE_ID, null);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        preferences.edit().putString(DEVICE_ID, created).apply();
        return created;
    }

    public void saveLoginHint(String serverUrl, String userName) {
        preferences.edit()
            .putString(SERVER, serverUrl)
            .putString(USER_NAME, userName)
            .apply();
    }

    public void save(JellyfinClient.Session session) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] encrypted = cipher.doFinal(session.token.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
            .putString(SERVER, session.serverUrl)
            .putString(USER_ID, session.userId)
            .putString(USER_NAME, session.userName)
            .putString(TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(TOKEN_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .apply();
    }

    public JellyfinClient.Session load() throws GeneralSecurityException {
        String encrypted = preferences.getString(TOKEN, null);
        String encodedIv = preferences.getString(TOKEN_IV, null);
        String server = preferences.getString(SERVER, null);
        String userId = preferences.getString(USER_ID, null);
        if (encrypted == null || encodedIv == null || server == null || userId == null) {
            return null;
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameters = new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP));
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), parameters);
        byte[] token = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
        return new JellyfinClient.Session(
            server,
            new String(token, StandardCharsets.UTF_8),
            userId,
            preferences.getString(USER_NAME, ""),
            true
        );
    }

    public String serverHint() {
        return preferences.getString(SERVER, "");
    }

    public String userNameHint() {
        return preferences.getString(USER_NAME, "");
    }

    public void clearSession() {
        preferences.edit()
            .remove(USER_ID)
            .remove(TOKEN)
            .remove(TOKEN_IV)
            .apply();
    }

    private SecretKey secretKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (IOException ex) {
            throw new GeneralSecurityException("Unable to load the Android keystore.", ex);
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return generator.generateKey();
    }
}
