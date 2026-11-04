package com.soutenance.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Service TOTP (Time-based One-Time Password) — RFC 6238.
 *
 * Implémentation pure Java, sans dépendance externe.
 * Compatible avec Google Authenticator, Microsoft Authenticator, Authy.
 *
 * Pas-de-temps : 30 secondes
 * Longueur du code : 6 chiffres
 * Algorithme : HMAC-SHA1
 * Tolérance : ±1 fenêtre (90 secondes au total) pour absorber les dérives.
 */
@Service
public class TotpService {

    private static final int    PERIODE_SECONDES = 30;
    private static final int    NB_CHIFFRES      = 6;
    private static final String ALGORITHME       = "HmacSHA1";
    /** Tolérance d'écart d'horloge entre le serveur et le téléphone (en pas). */
    private static final int    TOLERANCE_PAS    = 1;

    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Génère un secret aléatoire de 20 octets, encodé en Base32.
     * Format attendu par Google Authenticator.
     */
    public String genererSecret() {
        byte[] octets = new byte[20];
        RNG.nextBytes(octets);
        return base32Encode(octets);
    }

    /**
     * Construit l'URL otpauth:// scannable en QR code.
     * Le téléphone l'enregistre comme un nouveau compte.
     *
     * @param emetteur     nom de l'application (ex : "Soutenance HR")
     * @param compte       identifiant lisible (ex : "alice@mfb.sn")
     * @param secretBase32 secret partagé renvoyé par {@link #genererSecret()}
     */
    public String construireOtpauthUrl(String emetteur, String compte, String secretBase32) {
        String issuer  = URLEncoder.encode(emetteur, StandardCharsets.UTF_8);
        String account = URLEncoder.encode(compte,   StandardCharsets.UTF_8);
        return String.format(
            "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
            issuer, account, secretBase32, issuer, NB_CHIFFRES, PERIODE_SECONDES
        );
    }

    /**
     * URL d'image QR via le service public quickchart.io (zéro dépendance).
     * Pour une vraie prod, héberger un générateur local (zxing).
     */
    public String construireQrCodeUrl(String otpauthUrl) {
        String enc = URLEncoder.encode(otpauthUrl, StandardCharsets.UTF_8);
        return "https://quickchart.io/qr?text=" + enc + "&size=220";
    }

    /**
     * Vérifie le code saisi par l'utilisateur contre le secret.
     * Accepte une fenêtre de ±{@link #TOLERANCE_PAS} pour la tolérance horloge.
     */
    public boolean verifierCode(String secretBase32, String codeSaisi) {
        if (secretBase32 == null || codeSaisi == null) return false;
        codeSaisi = codeSaisi.trim();
        if (codeSaisi.length() != NB_CHIFFRES) return false;
        if (!codeSaisi.chars().allMatch(Character::isDigit)) return false;

        byte[] secret = base32Decode(secretBase32);
        if (secret.length == 0) return false;

        long pas = Instant.now().getEpochSecond() / PERIODE_SECONDES;
        int attendu;
        for (int delta = -TOLERANCE_PAS; delta <= TOLERANCE_PAS; delta++) {
            try {
                attendu = genererCode(secret, pas + delta);
                if (String.format("%0" + NB_CHIFFRES + "d", attendu).equals(codeSaisi)) {
                    return true;
                }
            } catch (Exception ignored) { /* essai suivant */ }
        }
        return false;
    }

    /** Génère le code TOTP pour un pas de temps donné. */
    private int genererCode(byte[] secret, long pas) throws Exception {
        byte[] buf = new byte[8];
        for (int i = 7; i >= 0; i--) { buf[i] = (byte) (pas & 0xff); pas >>= 8; }

        Mac mac = Mac.getInstance(ALGORITHME);
        mac.init(new SecretKeySpec(secret, ALGORITHME));
        byte[] h = mac.doFinal(buf);

        int offset = h[h.length - 1] & 0xf;
        int bin =  ((h[offset]     & 0x7f) << 24)
                |  ((h[offset + 1] & 0xff) << 16)
                |  ((h[offset + 2] & 0xff) << 8)
                |   (h[offset + 3] & 0xff);

        return bin % (int) Math.pow(10, NB_CHIFFRES);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Base32 RFC 4648 — encodage / décodage minimaux (sans padding strict)
    // ──────────────────────────────────────────────────────────────────────

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits  += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(B32.charAt((buffer >> bits) & 0x1f));
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 0x1f));
        return sb.toString();
    }

    private static byte[] base32Decode(String s) {
        if (s == null) return new byte[0];
        s = s.trim().toUpperCase().replace(" ", "").replace("=", "");
        if (s.isEmpty()) return new byte[0];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = B32.indexOf(s.charAt(i));
            if (v < 0) return new byte[0];
            buffer = (buffer << 5) | v;
            bits  += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
