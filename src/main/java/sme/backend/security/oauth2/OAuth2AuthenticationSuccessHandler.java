package sme.backend.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import sme.backend.security.UserPrincipal;
import sme.backend.security.jwt.JwtTokenProvider;
import sme.backend.util.CookieUtils;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @Value("${app.oauth2.authorized-redirect-uris}")
    private String[] authorizedRedirectUris;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String redirectUri = CookieUtils.getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(cookie -> {
                    String val = cookie.getValue();
                    // Fix for mutated cookies from deleteCookie
                    if (val == null || val.trim().isEmpty()) {
                        return authorizedRedirectUris[0];
                    }
                    return val;
                })
                .orElse(authorizedRedirectUris[0]);

        // Validate redirect_uri
        boolean isAuthorized = false;
        for (String authorizedRedirectUri : authorizedRedirectUris) {
            // Check host and port
            java.net.URI authorizedURI = java.net.URI.create(authorizedRedirectUri);
            java.net.URI clientRedirectUri = java.net.URI.create(redirectUri);

            if(authorizedURI.getHost().equalsIgnoreCase(clientRedirectUri.getHost())
                    && authorizedURI.getPort() == clientRedirectUri.getPort()) {
                isAuthorized = true;
                break;
            }
        }

        if(!isAuthorized) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error("invalid_redirect_uri"),
                    "Sorry! We've got an Unauthorized Redirect URI and can't proceed with the authentication");
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String jwtToken = tokenProvider.generateAccessToken(userPrincipal);
        
        // Create one-time code
        String oneTimeCode = UUID.randomUUID().toString();
        
        // Save to Redis: format {jwt}:{userId}
        String redisKey = "oauth2:code:" + oneTimeCode;
        String redisValue = jwtToken + ":" + userPrincipal.getId().toString();
        redisTemplate.opsForValue().set(redisKey, redisValue, 30, TimeUnit.SECONDS);

        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", oneTimeCode)
                .build().toUriString();
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequest(request, response);
    }
}
