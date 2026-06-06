package sme.backend.security.oauth2;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import sme.backend.entity.User;
import sme.backend.repository.UserRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.security.oauth2.user.OAuth2UserInfo;
import sme.backend.security.oauth2.user.OAuth2UserInfoFactory;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest oAuth2UserRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(oAuth2UserRequest);

        try {
            return processOAuth2User(oAuth2UserRequest, oAuth2User);
        } catch (OAuth2AuthenticationException ex) {
            // Rethrow OAuth2AuthenticationException so it can be handled by the failure handler
            throw ex;
        } catch (Exception ex) {
            // Wrap any other exception in OAuth2AuthenticationException
            throw new OAuth2AuthenticationException(new OAuth2Error("internal_error"), ex.getMessage());
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(oAuth2UserRequest.getClientRegistration().getRegistrationId(), oAuth2User.getAttributes());
        if (!StringUtils.hasText(oAuth2UserInfo.getEmail())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_found"), "Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(oAuth2UserInfo.getEmail());
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Auto-link logic: If the user registered via LOCAL but now logs in via Google
            // We trust Google as a provider for email verification, meaning if they can log in via Google
            // with this email, they own the email. We auto-link the account.
            if (!user.getPrimaryProvider().name().equalsIgnoreCase(oAuth2UserRequest.getClientRegistration().getRegistrationId())) {
                if (user.getPrimaryProvider() == User.AuthProvider.LOCAL) {
                    user.setIsOauth2Linked(true);
                    user.setProviderId(oAuth2UserInfo.getId());
                    if (!StringUtils.hasText(user.getAvatarUrl())) {
                        user.setAvatarUrl(oAuth2UserInfo.getImageUrl());
                    }
                    user = userRepository.save(user);
                } else {
                    // E.g. registered with Facebook, trying to log in with Google.
                    throw new OAuth2AuthenticationException(new OAuth2Error("provider_mismatch"),
                            "You signed up with " + user.getPrimaryProvider() + " account. Please use your " + user.getPrimaryProvider() + " account to login.");
                }
            } else {
                user = updateExistingUser(user, oAuth2UserInfo);
            }
        } else {
            user = registerNewUser(oAuth2UserRequest, oAuth2UserInfo);
        }

        return UserPrincipal.build(user, oAuth2User.getAttributes());
    }

    private User registerNewUser(OAuth2UserRequest oAuth2UserRequest, OAuth2UserInfo oAuth2UserInfo) {
        User user = new User();
        user.setPrimaryProvider(User.AuthProvider.valueOf(oAuth2UserRequest.getClientRegistration().getRegistrationId().toUpperCase()));
        user.setProviderId(oAuth2UserInfo.getId());
        user.setIsOauth2Linked(true);
        user.setFullName(oAuth2UserInfo.getName());
        user.setEmail(oAuth2UserInfo.getEmail());
        user.setUsername(oAuth2UserInfo.getEmail()); // Use email as username
        user.setAvatarUrl(oAuth2UserInfo.getImageUrl());
        user.setIsActive(true);
        user.setRole(User.UserRole.ROLE_CUSTOMER); // Default to customer
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); // Random password for OAuth2 users

        return userRepository.save(user);
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        boolean updated = false;
        if (!StringUtils.hasText(existingUser.getAvatarUrl()) && StringUtils.hasText(oAuth2UserInfo.getImageUrl())) {
            existingUser.setAvatarUrl(oAuth2UserInfo.getImageUrl());
            updated = true;
        }
        if (updated) {
            return userRepository.save(existingUser);
        }
        return existingUser;
    }
}
