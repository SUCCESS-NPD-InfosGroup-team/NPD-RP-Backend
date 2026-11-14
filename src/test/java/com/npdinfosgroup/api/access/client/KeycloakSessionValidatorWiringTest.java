package com.npdinfosgroup.api.access.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * The validator is part of the JWT decoder that Spring Boot builds, without any change to the security
 * configuration: a correctly signed, unexpired token is still checked against Keycloak. Keycloak points
 * to a closed port, so the check fails as not done.
 */
class KeycloakSessionValidatorWiringTest {

	/** Nothing listens on port 1: the connection is refused at once. */
	private static final String UNREACHABLE_ISSUER = "http://127.0.0.1:1/realms/npd";

	@TempDir
	Path keys;

	/**
	 * Boot's decoder verifies the signature with the public key of the settings and then runs the
	 * validator, which cannot reach Keycloak and refuses the token.
	 *
	 * @throws Exception if the keys cannot be created or the token signed
	 */
	@Test
	void theAutoConfiguredDecoderChecksTheSessionInKeycloak() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		Path publicKey = keys.resolve("public.pem");
		Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n"
				+ Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
		String token = signedToken((RSAPrivateKey) pair.getPrivate());

		new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(OAuth2ResourceServerAutoConfiguration.class))
			.withUserConfiguration(KeycloakAdminClientConfig.class, KeycloakIntrospectionConfig.class)
			.withPropertyValues("spring.security.oauth2.resourceserver.jwt.public-key-location=" + publicKey.toUri(),
					"app.keycloak.issuer-uri=" + UNREACHABLE_ISSUER, "app.keycloak.admin-client-id=npd-rp-api-admin",
					"app.keycloak.admin-client-secret=s3cret", "app.keycloak.connect-timeout=2s",
					"app.keycloak.read-timeout=5s", "app.keycloak.frontend-client-id=npd-rp-web",
					"app.keycloak.invitation-redirect-uri=http://localhost:5173/recursos",
					"app.keycloak.invitation-lifespan=48h")
			.run(context -> {
				JwtDecoder decoder = context.getBean(JwtDecoder.class);

				assertThatThrownBy(() -> decoder.decode(token)).isInstanceOfSatisfying(JwtValidationException.class,
						ex -> assertThat(ex.getErrors()).extracting(OAuth2Error::getErrorCode)
							.containsExactly(OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE));
			});
	}

	private static String signedToken(RSAPrivateKey privateKey) throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("user")
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(300)))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		jwt.sign(new RSASSASigner(privateKey));
		return jwt.serialize();
	}

}
