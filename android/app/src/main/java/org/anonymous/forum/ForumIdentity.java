package org.anonymous.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

/** App-level signing identity. The private key is local-only and is never serialized. */
final class ForumIdentity {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "anonymous_forum_identity_v1";
    private static final String LEGACY_PREFS = "forum_mesh_identity";
    private static final String LEGACY_PRIVATE = "private_key";
    private static final String LEGACY_PUBLIC = "public_key";
    private static final String LEGACY_ALGORITHM = "algorithm";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String algorithm;
    private final String publicKeyBase64;

    ForumIdentity(Context context) {
        KeyPair pair = loadOrCreate(context.getApplicationContext());
        privateKey = pair.getPrivate();
        publicKey = pair.getPublic();
        algorithm = publicKey.getAlgorithm();
        publicKeyBase64 = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }

    String publicKeyBase64() { return publicKeyBase64; }
    String algorithm() { return algorithm; }

    byte[] sign(byte[] message) throws Exception {
        Signature signer = Signature.getInstance(signatureAlgorithm(algorithm));
        signer.initSign(privateKey);
        signer.update(message);
        return signer.sign();
    }

    static boolean verify(String publicKeyBase64, byte[] message, byte[] signature) {
        try {
            byte[] encoded = Base64.decode(publicKeyBase64, Base64.DEFAULT);
            String keyAlgorithm = keyAlgorithm(encoded);
            PublicKey key = KeyFactory.getInstance(keyAlgorithm).generatePublic(new X509EncodedKeySpec(encoded));
            Signature verifier = Signature.getInstance(signatureAlgorithm(keyAlgorithm));
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static KeyPair loadOrCreate(Context context) {
        try {
            KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
            store.load(null);
            if (store.containsAlias(ALIAS)) {
                return keyPairFromStore(store);
            }
            generateInAndroidKeyStore(context, store);
            return keyPairFromStore(store);
        } catch (Exception ignored) {
            return loadOrCreateSoftware(context);
        }
    }

    private static KeyPair keyPairFromStore(KeyStore store) throws Exception {
        PrivateKey privateKey = (PrivateKey) store.getKey(ALIAS, null);
        PublicKey publicKey = store.getCertificate(ALIAS).getPublicKey();
        return new KeyPair(publicKey, privateKey);
    }

    private static void generateInAndroidKeyStore(Context context, KeyStore store) throws Exception {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .build();
                generator.initialize(spec);
                generator.generateKeyPair();
                return;
            } catch (Exception ignored) {
                // Some older Android Keystore providers lack EC signing; try RSA below.
                try { if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS); }
                catch (Exception cleanupIgnored) { }
            }
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE);
        long now = System.currentTimeMillis();
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                .setAlias(ALIAS)
                .setSubject(new javax.security.auth.x500.X500Principal("CN=Anonymous Forum"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(new Date(now - 60_000L))
                .setEndDate(new Date(now + 10L * 365L * 24L * 60L * 60L * 1000L))
                .build();
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    private static KeyPair loadOrCreateSoftware(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        String privateText = prefs.getString(LEGACY_PRIVATE, "");
        String publicText = prefs.getString(LEGACY_PUBLIC, "");
        String algorithm = prefs.getString(LEGACY_ALGORITHM, "EC");
        if (!privateText.isEmpty() && !publicText.isEmpty()) {
            try {
                KeyFactory factory = KeyFactory.getInstance(algorithm);
                PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privateText, Base64.DEFAULT)));
                PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.decode(publicText, Base64.DEFAULT)));
                return new KeyPair(publicKey, privateKey);
            } catch (Exception ignored) {
                // A corrupt local key is replaced; no network identity is inferred from the old value.
            }
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            prefs.edit()
                    .putString(LEGACY_PRIVATE, Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP))
                    .putString(LEGACY_PUBLIC, Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP))
                    .putString(LEGACY_ALGORITHM, pair.getPublic().getAlgorithm())
                    .apply();
            return pair;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create the local forum signing identity", error);
        }
    }

    private static String keyAlgorithm(byte[] encoded) throws Exception {
        try {
            KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
            return "EC";
        } catch (Exception ignored) {
            KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            return "RSA";
        }
    }

    private static String signatureAlgorithm(String keyAlgorithm) {
        return "EC".equalsIgnoreCase(keyAlgorithm) ? "SHA256withECDSA" : "SHA256withRSA";
    }
}
