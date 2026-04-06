package com.scriptoria.service;

import com.scriptoria.dto.AuthResponse;
import com.scriptoria.dto.LoginRequest;
import com.scriptoria.dto.RegisterRequest;
import com.scriptoria.dto.UserProfileResponse;
import com.scriptoria.entity.User;
import com.scriptoria.entity.UserRole;
import com.scriptoria.repository.AnalysisHistoryRepository;
import com.scriptoria.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setRole(UserRole.USER);
        User saved = userRepository.save(user);

        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setCreatedAt(user.getCreatedAt());
        response.setTotalAnalyses((int) analysisHistoryRepository.countByUser(user));
        return response;
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();

        String token = jwtService.generateToken(userDetails);

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setUserId(user.getId());
        response.setExpiresIn(jwtService.getExpirationMs() / 1000);
        return response;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
