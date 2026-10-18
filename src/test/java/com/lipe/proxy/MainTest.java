package com.lipe.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    @DisplayName("Deve retornar saudação formatada")
    void shouldReturnGreeting() {
        assertThat(Main.greet("Zed")).isEqualTo("Hello, Zed!");
    }
}
