package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.LoginRequest;
import com.att.tdp.issueflow.dto.LoginResponse;
import com.att.tdp.issueflow.dto.UserProfileResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.JwtTokenProvider;
import com.att.tdp.issueflow.security.TokenDenyList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenDenyList denyList;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String token = tokenProvider.generateToken(request.getUsername());
        return new LoginResponse(token, "Bearer", tokenProvider.getExpirationMs() / 1000);
    }

    public void logout(String token) {
        denyList.deny(token);
    }

    public UserProfileResponse me(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return UserProfileResponse.from(user);
    }
}
