package com.esg.security;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * MFA enforcement switch (TRD §5.4, S1). TOTP is mandatory for users holding a role flagged {@code mfa_required}
 * (SUPER_ADMIN, TENANT_ADMIN, ESG_DIRECTOR) in every environment. {@code esg.security.mfa.dev-bypass} turns that
 * off for local experiments only: it is off by default, logs a warning at startup whenever it is on, and the
 * application refuses to start with it under the staging or prod profile.
 */
@Component
public class MfaPolicy {

    private static final Logger log = LoggerFactory.getLogger(MfaPolicy.class);

    private final boolean devBypass;

    public MfaPolicy(@Value("${esg.security.mfa.dev-bypass:false}") boolean devBypass, Environment env) {
        this.devBypass = devBypass;
        if (devBypass) {
            if (Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("staging"))) {
                throw new IllegalStateException("esg.security.mfa.dev-bypass must not be enabled in staging or prod");
            }
            log.warn("SECURITY WARNING: esg.security.mfa.dev-bypass is ON. Users with MFA-required roles can sign in "
                    + "without a verification code. Development only; never enable this in a shared environment.");
        }
    }

    /** True when this user must hold an MFA-verified session. */
    public boolean required(UserAccess access) {
        return !devBypass && access.mfaRequired();
    }

    public boolean devBypass() {
        return devBypass;
    }
}
