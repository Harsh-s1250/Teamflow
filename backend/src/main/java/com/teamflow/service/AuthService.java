package com.teamflow.service;

import com.teamflow.dto.auth.LoginRequest;
import com.teamflow.dto.auth.LoginResponse;
import com.teamflow.entity.User;
import com.teamflow.repository.UserRepository;
import com.teamflow.security.AppUserPrincipal;
import com.teamflow.security.JwtProperties;
import com.teamflow.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                        JwtProperties jwtProperties, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            // Deliberately identical message whether the email doesn't exist
            // or the password is wrong, so login cannot be used to enumerate
            // registered email addresses.
            throw new BadCredentialsException("Invalid email or password.");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        AppUserPrincipal principal = new AppUserPrincipal(user);
        String token = jwtService.generateToken(principal);

        return new LoginResponse(
                token,
                jwtProperties.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
