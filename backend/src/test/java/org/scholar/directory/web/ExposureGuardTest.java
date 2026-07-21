package org.scholar.directory.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExposureGuardTest {
    @Test
    void recognizesOnlyLoopbackAddresses() {
        assertThat(ExposureGuard.isLoopback("127.0.0.1")).isTrue();
        assertThat(ExposureGuard.isLoopback("::1")).isTrue();
        assertThat(ExposureGuard.isLoopback("0.0.0.0")).isFalse();
    }

    @Test
    void refusesExternalBindingWithoutToken() {
        assertThatThrownBy(() -> new ExposureGuard("0.0.0.0", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing non-loopback API binding");
    }

    @Test
    void allowsExternalBindingWithToken() {
        assertThatCode(() -> new ExposureGuard("0.0.0.0", "configured-token").run(null))
                .doesNotThrowAnyException();
    }
}
