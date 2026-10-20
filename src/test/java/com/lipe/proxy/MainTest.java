package com.lipe.proxy;

import com.lipe.proxy.security.Auth;
import com.lipe.proxy.security.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    @DisplayName("RateLimiter: Deve permitir conexões até o limite por IP e liberar vaga ao desconectar")
    void shouldEnforceConnectionLimitAndRecoverAfterRelease() {
        RateLimiter rateLimiter = new RateLimiter(2);
        int fakeClientIp = 0x7F000001; // 127.0.0.1

        assertThat(rateLimiter.allowConnection(fakeClientIp)).isTrue();
        assertThat(rateLimiter.allowConnection(fakeClientIp)).isTrue();
        assertThat(rateLimiter.allowConnection(fakeClientIp)).isFalse(); // 3ª tentativa bloqueada
        assertThat(rateLimiter.getActiveConnectionsCount(fakeClientIp)).isEqualTo(2);

        rateLimiter.releaseConnection(fakeClientIp);
        assertThat(rateLimiter.allowConnection(fakeClientIp)).isTrue();  // Vaga liberada!
    }

    @Test
    @DisplayName("Auth: Deve aceitar login/senha corretos e recusar credenciais erradas")
    void shouldAuthenticateValidUserAndRejectInvalidCredentials() {
        Auth auth = new Auth();
        auth.registerUser("lipe", "123456");

        // Login e senha corretos -> true
        assertThat(auth.authenticate("AUTH lipe 123456")).isTrue();

        // Senha errada, usuário inexistente ou comando mal formatado -> false
        assertThat(auth.authenticate("AUTH lipe senha_errada")).isFalse();
        assertThat(auth.authenticate("AUTH outro 123456")).isFalse();
        assertThat(auth.authenticate("oi tudo bem")).isFalse();
    }
}
