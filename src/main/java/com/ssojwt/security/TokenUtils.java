package com.ssojwt.security;

import com.ssojwt.model.security.SpringUserDetails;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;

import java.util.*;

import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mobile.device.Device;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class TokenUtils {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String AUDIENCE_UNKNOWN   = "unknown";
    private final String AUDIENCE_WEB       = "web";
    private final String AUDIENCE_MOBILE    = "mobile";
    private final String AUDIENCE_TABLET    = "tablet";

    private final String secret = "sssshhhh!";

    private final String issuer = "com.ssojwt.security";


    private Long expiration = 604800l;

    static JsonWebKey jwKey = null;

    static {
        // Setting up Direct Symmetric Encryption and Decryption
        String jwkJson = "{\"kty\":\"oct\", \"k\":\"9d6722d6-b45c-4dcb-bd73-2e057c44eb93-928390\"}";
        try {
            new JsonWebKey.Factory();
            jwKey = JsonWebKey.Factory.newJwk(jwkJson);
        } catch (JoseException e) {
            e.printStackTrace();
        }
    }

    private NumericDate generateExpirationDate() {
        return NumericDate.fromMilliseconds(System.currentTimeMillis() + this.expiration * 1000);
        //return new Date(System.currentTimeMillis() + this.expiration * 1000);
    }

    private String generateAudience(Device device) {
        String audience = this.AUDIENCE_UNKNOWN;
        if (device.isNormal()) {
            audience = this.AUDIENCE_WEB;
        } else if (device.isTablet()) {
            audience = AUDIENCE_TABLET;
        } else if (device.isMobile()) {
            audience = AUDIENCE_MOBILE;
        }
        return audience;
    }

    public String generateToken(UserDetails userDetails, Device device) {
        JwtClaims claims = new JwtClaims();
        claims.setIssuer(this.issuer);
        claims.setExpirationTime(generateExpirationDate());
        claims.setGeneratedJwtId();
        claims.setNotBeforeMinutesInThePast(2);
        claims.setSubject(userDetails.getUsername());
        //claims.setAudience(generateAudience(device));
        claims.setAudience("audience");
        claims.setIssuedAtToNow();

        Collection<SimpleGrantedAuthority> grantedAuthorities = (Collection<SimpleGrantedAuthority>)userDetails.getAuthorities();
        List<String> authorities = new ArrayList<String>();
        for (SimpleGrantedAuthority grantedAuthority : grantedAuthorities) {
            authorities.add(grantedAuthority.getAuthority());
        }
        claims.setStringListClaim("authorities", authorities);
        claims.setStringClaim("uid", userDetails.getUsername());

        JsonWebSignature jws = new JsonWebSignature();

        logger.info("Claims => " + claims.toJson());
        // The payload of the JWS is JSON content of the JWT Claims
        jws.setPayload(claims.toJson());
        jws.setKeyIdHeaderValue(jwKey.getKeyId());
        jws.setKey(jwKey.getKey());

        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);

        String jwt = null;
        try {
            jwt = jws.getCompactSerialization();
        } catch (JoseException e) {
            e.printStackTrace();
        }

        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.DIRECT);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
        jwe.setKey(jwKey.getKey());
        jwe.setKeyIdHeaderValue(jwKey.getKeyId());
        jwe.setContentTypeHeaderValue("JWT");
        jwe.setPayload(jwt);

        String jweSerialization = null;
        try {
            jweSerialization = jwe.getCompactSerialization();
        } catch (JoseException e) {
            e.printStackTrace();
        }

        return jweSerialization;
    }

    public JwtClaims getClaimsFromToken(String token) throws InvalidJwtException {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setExpectedAudience("audience")
                .setExpectedIssuer(this.issuer)
                .setDecryptionKey(jwKey.getKey())
                .setVerificationKey(jwKey.getKey()).build();
        return  jwtConsumer.processToClaims(token);
    }

    public String getUsernameFromClaims(JwtClaims claims) throws MalformedClaimException {
        return claims.getStringClaimValue("uid");
    }

    public Date getExpirationDateFromClaims(JwtClaims claims) throws MalformedClaimException {
        return new Date(claims.getExpirationTime().getValueInMillis());
    }

    public Date getIssuedAtFromClaims(JwtClaims claims) throws MalformedClaimException {
        return new Date(claims.getIssuedAt().getValueInMillis());
    }

    private Boolean isIssuedBeforeLastPasswordReset(Date issuedAt, Date lastPasswordReset) {
        return (lastPasswordReset != null && issuedAt.before(lastPasswordReset));
    }

    private Boolean isTokenExpired(JwtClaims claims) throws MalformedClaimException {
        final Date expiration = this.getExpirationDateFromClaims(claims);
        return expiration.before(new Date());
    }

    private Boolean ignoreTokenExpiration(JwtClaims claims) throws MalformedClaimException {
        List<String> audiences = claims.getAudience();
        return (audiences.contains(this.AUDIENCE_MOBILE) || audiences.contains(this.AUDIENCE_TABLET));
    }

    public Boolean canTokenBeRefreshed(JwtClaims claims, Date lastPasswordReset) throws MalformedClaimException {
        Date issuedAt = new Date(claims.getIssuedAt().getValueInMillis());
        return (
                !(this.isIssuedBeforeLastPasswordReset(issuedAt, lastPasswordReset))
                        && (!(this.isTokenExpired(claims)) || this.ignoreTokenExpiration(claims))
        );
    }

    public Boolean validateToken(JwtClaims claims, UserDetails userDetails) throws MalformedClaimException {
        SpringUserDetails springUserDetails = (SpringUserDetails) userDetails;
        final String username = this.getUsernameFromClaims(claims);
        final Date issuedAt = this.getIssuedAtFromClaims(claims);
        return (username.equals(springUserDetails.getUsername()) && !(this.isTokenExpired(claims)) && !(this.isIssuedBeforeLastPasswordReset(issuedAt, springUserDetails.getLastPasswordReset())));
    }

}