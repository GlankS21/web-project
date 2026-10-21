package com.shop.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "CONFIG_SERVER_USERNAME=test-user",
        "CONFIG_SERVER_PASSWORD=test-password"
})
@AutoConfigureMockMvc
class ConfigServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void configurationRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/application/default"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void servesSharedConfigurationWithUnresolvedSecretPlaceholders() throws Exception {
        mockMvc.perform(get("/application/default").with(httpBasic("test-user", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("application"))
                .andExpect(jsonPath("$.propertySources[0].source['spring.jpa.hibernate.ddl-auto']").value("validate"))
                // Secrets must be resolved by the client, never by the config server.
                .andExpect(jsonPath("$.propertySources[0].source['spring.rabbitmq.password']")
                        .value("${RABBITMQ_PASSWORD}"));
    }
}
