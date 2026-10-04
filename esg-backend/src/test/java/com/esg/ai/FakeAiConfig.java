package com.esg.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the HTTP AI engine with {@link FakeAiEngine} in the tests that import it. */
@TestConfiguration
public class FakeAiConfig {

    @Bean
    @Primary
    AiEngine fakeAiEngine() {
        return new FakeAiEngine();
    }
}
