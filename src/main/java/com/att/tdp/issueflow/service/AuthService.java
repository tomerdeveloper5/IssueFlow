package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.AuthLoginRequest;
import com.att.tdp.issueflow.api.dto.AuthTokenResponse;
import com.att.tdp.issueflow.api.dto.UserResponse;
import com.att.tdp.issueflow.domain.User;
import com.att.tdp.issueflow.security.JwtService;
import com.att.tdp.issueflow.security.SecurityProperties;
import com.att.tdp.issueflow.security.TokenDenyListService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final TokenDenyListService denyListService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public AuthTokenResponse login(AuthLoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String token = jwtService.generateToken(request.username());
        auditLogService.logUserAction("LOGIN", "AUTH", 0L);
        return new AuthTokenResponse(token, "Bearer", securityProperties.expirationSeconds());
    }

    public void logout(String token) {
        denyListService.deny(token, jwtService.extractExpiration(token));
        auditLogService.logUserAction("LOGOUT", "AUTH", 0L);
    }

    public UserResponse me(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}


