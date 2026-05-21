package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.UserCreateRequest;
import com.att.tdp.issueflow.api.dto.UserMentionsResponse;
import com.att.tdp.issueflow.api.dto.UserResponse;
import com.att.tdp.issueflow.api.dto.UserUpdateRequest;
import com.att.tdp.issueflow.service.CommentService;
import com.att.tdp.issueflow.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final CommentService commentService;

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAll();
    }

    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable Long userId) {
        return userService.getById(userId);
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PostMapping("/update/{userId}")
    public ResponseEntity<Void> updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
        userService.update(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.delete(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/mentions")
    public UserMentionsResponse getMentions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return commentService.getMentionsForUser(userId, page, pageSize);
    }
}


