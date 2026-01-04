package com.uit.sharedkernel.security;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityDataConfig {

    @Bean
    public SimpleModule xssSanitizationModule(XssSanitizer xssSanitizer) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new HtmlSanitizationDeserializer(xssSanitizer));
        return module;
    }
}
