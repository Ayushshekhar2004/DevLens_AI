package com.devlensai.backend.service;

import com.devlensai.backend.dto.AuthResponse;
import com.devlensai.backend.dto.LoginRequest;
import com.devlensai.backend.dto.RegisterRequest;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.exception.EmailAlreadyRegisteredException;
import com.devlensai.backend.exception.InvalidCredentialsException;
import com.devlensai.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String JWT_SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private UserRepository repository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(repository, passwordEncoder, new JwtService(JWT_SECRET, 60));
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyBcryptHash() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest(" Ada Lovelace ", " ADA@Example.COM ", "strong-pass"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Ada Lovelace");
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("strong-pass");
        assertThat(passwordEncoder.matches("strong-pass", saved.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejected() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Ada", "ada@example.com", "strong-pass")
        )).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginReturnsSignedTokenForMatchingPassword() {
        User user = new User("Ada", "ada@example.com", passwordEncoder.encode("strong-pass"));
        when(repository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("ADA@example.com", "strong-pass"));

        assertThat(response.token()).hasSizeGreaterThan(40).contains(".");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void loginUsesSameGenericErrorForUnknownEmailAndWrongPassword() {
        when(repository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        User user = new User("Ada", "ada@example.com", passwordEncoder.encode("strong-pass"));
        when(repository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "strong-pass")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrong-pass")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
    }
}
