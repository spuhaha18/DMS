package com.lab.edms.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class AuthControllerIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired ObjectMapper json;

    @Test
    void postLogin_correctCredentials_returns200WithUserSummary() throws Exception {
        seed("ivy", "Correct!Pass1");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("ivy", "Correct!Pass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ivy"))
                .andExpect(jsonPath("$.fullName").value("ivy"))
                .andExpect(jsonPath("$.forceChangePw").value(false));
    }

    @Test
    void postLogin_wrongPassword_returns401_withProblemDetail() throws Exception {
        seed("jack", "Correct!Pass1");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("jack", "bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.remaining_attempts").value(4));
    }

    @Test
    void postLogin_lockedAccount_returns401_withCodeAUTH_002() throws Exception {
        seed("kate", "Correct!Pass1");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new LoginRequest("kate", "x"))));
        }

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("kate", "Correct!Pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void getMe_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    private void seed(String userId, String pw) {
        userRepo.save(LocalAuthProviderIT.buildUser(userId, encoder.encode(pw)));
    }
}
