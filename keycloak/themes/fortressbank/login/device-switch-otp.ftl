<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        Device Switch Verification
    <#elseif section = "form">
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="otp" class="${properties.kcLabelClass!}">
                    A device is already logged in. Please enter the OTP sent to your phone to switch devices.
                </label>

                <input type="text" id="otp" name="otp" class="${properties.kcInputClass!}" 
                       autocomplete="off" autofocus placeholder="Enter 6-digit OTP" 
                       pattern="[0-9]{6}" maxlength="6" required />
            </div>

            <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                <div id="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" 
                           name="login" id="kc-login" type="submit" value="Verify OTP"/>
                </div>
            </div>
        </form>
    <#elseif section = "info" >
        <#if message?has_content>
            <div class="alert alert-${message.type}">
                <#if message.type = 'success'>
                    <span class="${properties.kcFeedbackSuccessIcon!}"></span>
                </#if>
                <#if message.type = 'warning'>
                    <span class="${properties.kcFeedbackWarningIcon!}"></span>
                </#if>
                <#if message.type = 'error'>
                    <span class="${properties.kcFeedbackErrorIcon!}"></span>
                </#if>
                <#if message.type = 'info'>
                    <span class="${properties.kcFeedbackInfoIcon!}"></span>
                </#if>
                <span class="kc-feedback-text">${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
