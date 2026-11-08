package com.courier.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Genera un par de claves RSA, expone la pública en un archivo PEM (como lo haría el
 * Compose con JWT_PUBLIC_KEY_LOCATION) y firma JWT RS256 de prueba.
 * @author TheMiggaCool
 */
final class JwtTest {

    private static final KeyPair KEY_PAIR = generarPar();
    private static final KeyPair OTRO_PAR = generarPar();
    private static final Path PUBLIC_KEY_FILE = escribirClavePublica();

    private JwtTest() {
    }

    /** URL file: de la clave pública, para spring.security.oauth2.resourceserver.jwt.public-key-location. */
    static String publicKeyLocation() {
        return PUBLIC_KEY_FILE.toUri().toString();
    }

    static String token(UUID sub, String... roles) {
        return firmar(KEY_PAIR, sub, roles, new Date(System.currentTimeMillis() + 3_600_000));
    }

    static String tokenExpirado(UUID sub, String... roles) {
        return firmar(KEY_PAIR, sub, roles, new Date(System.currentTimeMillis() - 3_600_000));
    }

    /** Firmado con una clave distinta a la publicada: la firma NO debe validar. */
    static String tokenConFirmaInvalida(UUID sub, String... roles) {
        return firmar(OTRO_PAR, sub, roles, new Date(System.currentTimeMillis() + 3_600_000));
    }

    private static String firmar(KeyPair par, UUID sub, String[] roles, Date expiracion) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sub.toString())
                    .issuer("logitrack-security")
                    .claim("roles", List.of(roles))
                    .issueTime(new Date(System.currentTimeMillis() - 7_200_000))
                    .expirationTime(expiracion)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) par.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generarPar() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path escribirClavePublica() {
        try {
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(KEY_PAIR.getPublic().getEncoded());
            Path file = Files.createTempFile("jwt-public-", ".pem");
            Files.writeString(file, "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
