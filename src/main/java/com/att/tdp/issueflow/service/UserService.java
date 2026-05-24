package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.MentionsPageResponse;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final CommentRepository commentRepository;

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse getById(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role(request.getRole())
                .password(request.getPassword() != null
                        ? passwordEncoder.encode(request.getPassword()) : null)
                .build();
        User saved = userRepository.save(user);
        auditLogService.log("CREATE", "USER", saved.getId(), ActorType.USER);
        return UserResponse.from(saved);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        User saved = userRepository.save(user);
        auditLogService.log("UPDATE", "USER", id, ActorType.USER);
        return UserResponse.from(saved);
    }

    public void delete(Long id) {
        findOrThrow(id);
        userRepository.deleteById(id);
        auditLogService.log("DELETE", "USER", id, ActorType.USER);
    }

    @Transactional
    public MentionsPageResponse getMentions(Long userId, int page, int pageSize) {
        findOrThrow(userId);
        var found = commentRepository.findAllByMentionedUsers_Id(
                userId, PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending()));
        return MentionsPageResponse.builder()
                .data(found.getContent().stream().map(CommentResponse::from).toList())
                .total(found.getTotalElements())
                .page(page)
                .build();
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
