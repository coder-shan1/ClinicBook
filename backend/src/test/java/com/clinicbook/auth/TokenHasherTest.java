package com.clinicbook.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void hashesTokensAsDeterministicSha256Hex() {
        assertThat(tokenHasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .hasSize(64);
        assertThat(tokenHasher.hash("abc")).isEqualTo(tokenHasher.hash("abc"));
    }

    @Test
    void generatesUrlSafeTokensWithEnoughEntropy() {
        String first = tokenHasher.newToken();
        String second = tokenHasher.newToken();

        assertThat(first).hasSize(43).doesNotContain("=", "+", "/");
        assertThat(second).isNotEqualTo(first);
    }
}
