package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.UserCreateRequest;
import com.att.tdp.issueflow.api.dto.UserResponse;
import com.att.tdp.issueflow.api.dto.UserUpdateRequest;
import com.att.tdp.issueflow.domain.User;
import com.att.tdp.issueflow.domain.UserRole;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        auditLogService.logUserAction("CREATE", "USER", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void update(Long userId, UserUpdateRequest request) {
        User user = getEntity(userId);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        userRepository.save(user);
        auditLogService.logUserAction("UPDATE", "USER", userId);
    }

    @Transactional
    public void delete(Long userId) {
        User user = getEntity(userId);
        userRepository.delete(user);
        auditLogService.logUserAction("DELETE", "USER", userId);
    }

    @Transactional(readOnly = true)
    public User getEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public List<User> getDevelopersOrderedByRegistration() {
        return userRepository.findByRoleOrderByCreatedAtAsc(UserRole.DEVELOPER);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}


