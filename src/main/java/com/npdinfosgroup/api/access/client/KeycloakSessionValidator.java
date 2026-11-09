package com.npdinfosgroup.api.access.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Rejects access tokens whose Keycloak session has ended, for example after the user logged out.
 * <p>
 * The JWT decoder only checks the token locally (signature, expiry, issuer), and logout does not change
 * the token, so this validator asks Keycloak's introspection endpoint whether it is still active. It runs
 * on every authenticated request, after the decoder has verified the signature.
 * <p>
 * It fails closed: if Keycloak cannot answer, the token is refused with
 * {@link OAuth2ErrorCodes#TEMPORARILY_UNAVAILABLE}, so an outage is told apart from an ended session.
 * See {@code docs/specs/SCRUM-159/design.md}.
 */
class KeycloakSessionValidator implements OAuth2TokenValidator<Jwt> {

	private static final Logger log = LoggerFactory.getLogger(KeycloakSessionValidator.class);

	private static final OAuth2Error SESSION_ENDED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"The session of the token has ended", null);

	private static final OAuth2Error NOT_CHECKED = new OAuth2Error(OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE,
			"Keycloak could not check the token", null);

	private static final ParameterizedTypeReference<Map<String, Object>> ANSWER = new ParameterizedTypeReference<>() {
	};

	private final RestClient keycloak;

	/**
	 * Creates the validator.
	 *
	 * @param keycloak client of the realm's introspection endpoint, authenticated as the service account
	 */
	KeycloakSessionValidator(RestClient keycloak) {
		this.keycloak = keycloak;
	}

	/**
	 * Asks Keycloak whether the token is still active.
	 *
	 * @param token the access token, already verified by the decoder
	 * @return success when Keycloak says it is active; {@link OAuth2ErrorCodes#INVALID_TOKEN} when it is
	 *         not; {@link OAuth2ErrorCodes#TEMPORARILY_UNAVAILABLE} when Keycloak could not be asked
	 */
	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("token", token.getTokenValue());
		form.add("token_type_hint", "access_token");
		Map<String, Object> answer;
		try {
			answer = keycloak.post().contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(ANSWER);
		}
		catch (RestClientException ex) {
			// Never the token: only what went wrong
			log.warn("Keycloak could not check the token: {}", ex.getClass().getName());
			return OAuth2TokenValidatorResult.failure(NOT_CHECKED);
		}
		boolean active = answer != null && Boolean.TRUE.equals(answer.get("active"));
		return active ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(SESSION_ENDED);
	}

}
