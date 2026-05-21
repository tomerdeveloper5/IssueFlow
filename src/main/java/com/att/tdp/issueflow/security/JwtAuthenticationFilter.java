package com.att.tdp.issueflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.ExpiredJwtException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final IssueFlowUserDetailsService userDetailsService;
    private final TokenDenyListService denyListService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            request.setAttribute("auth.errorCode", "AUTH_REQUIRED");
            request.setAttribute("auth.message", "Missing bearer token");
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        if (denyListService.isDenied(jwt)) {
            request.setAttribute("auth.errorCode", "AUTH_LOGGED_OUT_TOKEN");
            request.setAttribute("auth.message", "Token is no longer valid");
            filterChain.doFilter(request, response);
            return;
        }

        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (ExpiredJwtException ex) {
            request.setAttribute("auth.errorCode", "AUTH_TOKEN_EXPIRED");
            request.setAttribute("auth.message", "Bearer token has expired");
            filterChain.doFilter(request, response);
            return;
        } catch (Exception ex) {
            request.setAttribute("auth.errorCode", "AUTH_INVALID_TOKEN");
            request.setAttribute("auth.message", "Bearer token is invalid");
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                request.setAttribute("auth.errorCode", "AUTH_INVALID_TOKEN");
                request.setAttribute("auth.message", "Bearer token is invalid");
            }
        }
        filterChain.doFilter(request, response);
    }
}


