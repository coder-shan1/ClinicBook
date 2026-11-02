package com.clinicbook.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().clinicBookOpenApi();

    @Test
    void exposesApiMetadata() {
        assertThat(openApi.getInfo()).isNotNull();
        assertThat(openApi.getInfo().getTitle()).isEqualTo("ClinicBook API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
    }
}
