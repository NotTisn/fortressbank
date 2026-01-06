package com.uit.fortressbank.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class DeviceSwitchOtpAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(DeviceSwitchOtpAuthenticator.class);
    
    private static final String OTP_FORM_FIELD = "otp_code";
    private static final String CONFIG_USER_SERVICE_URL = "userServiceUrl";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Check if this is a pending device switch scenario
        String pendingSwitch = context.getAuthenticationSession() != null 
            ? context.getAuthenticationSession().getAuthNote(SingleDeviceAuthenticator.PENDING_DEVICE_SWITCH_NOTE)
            : null;
        
        if (!"true".equals(pendingSwitch)) {
            // No pending device switch, skip this authenticator
            context.success();
            return;
        }
        
        // Show OTP input form
        Response challenge = context.form()
                .setAttribute("otpRequired", true)
                .createForm("device-switch-otp.ftl");
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String otpCode = formData.getFirst(OTP_FORM_FIELD);
        
        if (otpCode == null || otpCode.isBlank()) {
            context.form()
                    .setError("missingOtpCode")
                    .setAttribute("otpRequired", true)
                    .createForm("device-switch-otp.ftl");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, 
                    context.form().createForm("device-switch-otp.ftl"));
            return;
        }
        
        UserModel user = context.getUser();
        if (user == null) {
            LOG.error("User is null in DeviceSwitchOtpAuthenticator action");
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }
        
        String newDeviceId = context.getAuthenticationSession() != null
                ? context.getAuthenticationSession().getAuthNote("newDeviceId")
                : null;
        
        // Verify OTP with user-service
        boolean otpValid = verifyDeviceSwitchOtp(context, user.getId(), otpCode);
        
        if (otpValid) {
            LOG.infof("Device switch OTP verified successfully for user: %s. Removing old sessions.", user.getUsername());
            
            // Remove all existing sessions (kick device A)
            RealmModel realm = context.getRealm();
            KeycloakSession session = context.getSession();
            
            List<UserSessionModel> existingSessions = session.sessions()
                    .getUserSessionsStream(realm, user)
                    .collect(Collectors.toList());
            
            for (UserSessionModel existing : existingSessions) {
                LOG.debugf("Removing session: %s", existing.getId());
                session.sessions().removeUserSession(realm, existing);
            }
            
            // Remove offline sessions
            session.sessions().getOfflineUserSessionsStream(realm, user)
                    .forEach(offlineSession -> session.sessions().removeOfflineUserSession(realm, offlineSession));
            
            // Update device ID in auth session for new login
            if (context.getAuthenticationSession() != null && newDeviceId != null) {
                context.getAuthenticationSession().setUserSessionNote(SingleDeviceAuthenticator.DEVICE_ID_NOTE_KEY, newDeviceId);
            }
            
            // Clear pending device switch flags
            if (context.getAuthenticationSession() != null) {
                context.getAuthenticationSession().removeAuthNote(SingleDeviceAuthenticator.PENDING_DEVICE_SWITCH_NOTE);
                context.getAuthenticationSession().removeAuthNote("newDeviceId");
            }
            
            context.success();
        } else {
            LOG.warnf("Invalid device switch OTP for user: %s", user.getUsername());
            Response challenge = context.form()
                    .setError("invalidOtpCode")
                    .setAttribute("otpRequired", true)
                    .createForm("device-switch-otp.ftl");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // No-op
    }
    
    /**
     * Verifies device switch OTP by calling user-service API
     */
    private boolean verifyDeviceSwitchOtp(AuthenticationFlowContext context, String userId, String otpCode) {
        String userServiceUrl = getStringConfig(context, CONFIG_USER_SERVICE_URL, "http://user-service:4000");
        String apiUrl = userServiceUrl + "/api/users/device-switch/verify-otp";
        
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            String jsonPayload = String.format("{\"userId\":\"%s\",\"otpCode\":\"%s\"}", userId, otpCode);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                // Read response to check validity
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String response = br.lines().collect(Collectors.joining());
                    LOG.debugf("OTP verification response: %s", response);
                    return response.contains("\"valid\":true") || response.contains("\"success\":true");
                }
            } else {
                LOG.warnf("Failed to verify device switch OTP for user: %s. Response code: %d", userId, responseCode);
                return false;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error calling user-service to verify device switch OTP for user: %s", userId);
            return false;
        }
    }
    
    private String getStringConfig(AuthenticationFlowContext context, String key, String defaultValue) {
        String value = context.getAuthenticatorConfig() != null
                ? context.getAuthenticatorConfig().getConfig().get(key)
                : null;
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
