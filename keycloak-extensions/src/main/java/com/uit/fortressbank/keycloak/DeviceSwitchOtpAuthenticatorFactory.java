package com.uit.fortressbank.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

public class DeviceSwitchOtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "device-switch-otp-authenticator";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty userServiceUrl = new ProviderConfigProperty();
        userServiceUrl.setName("userServiceUrl");
        userServiceUrl.setLabel("User Service URL");
        userServiceUrl.setType(ProviderConfigProperty.STRING_TYPE);
        userServiceUrl.setDefaultValue("http://user-service:4000");
        userServiceUrl.setHelpText("Base URL of the user-service for OTP verification");
        CONFIG_PROPERTIES.add(userServiceUrl);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Device Switch OTP Authenticator";
    }

    @Override
    public String getHelpText() {
        return "Handles OTP verification for device switching scenarios";
    }

    @Override
    public String getReferenceCategory() {
        return "device-switch-otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new DeviceSwitchOtpAuthenticator();
    }

    @Override
    public void init(Config.Scope config) {
        // No global config
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.CONDITIONAL,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }
}
