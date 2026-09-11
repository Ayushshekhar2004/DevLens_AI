package com.devlensai.backend.config;

import com.devlensai.backend.controller.AnalysisController;
import com.devlensai.backend.controller.HealthController;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.repository.UserRepository;
import com.devlensai.backend.service.AnalysisService;
import com.devlensai.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AnalysisController.class, HealthController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void rejectsAnalysisRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("A valid Bearer token is required"));
    }

    @Test
    void rejectsAnalyticsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnalysisRequestWithInvalidToken() throws Exception {
        when(jwtService.extractSubject("invalid-token"))
                .thenThrow(new IllegalArgumentException("invalid token"));

        mockMvc.perform(get("/api/analyses")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesValidTokenAndPassesUserToAnalysisLayer() throws Exception {
        User user = new User("Ada Lovelace", "ada@example.com", "password-hash");
        when(jwtService.extractSubject("valid-token")).thenReturn("ada@example.com");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(analysisService.findAllNewestFirst(user)).thenReturn(List.of());

        mockMvc.perform(get("/api/analyses")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(analysisService).findAllNewestFirst(user);
    }

    @Test
    void keepsHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
