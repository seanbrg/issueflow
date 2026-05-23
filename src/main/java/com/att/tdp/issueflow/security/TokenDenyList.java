package com.att.tdp.issueflow.security;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class TokenDenyList {

    private final Set<String> deniedTokens = Collections.synchronizedSet(new HashSet<>());

    public void deny(String token) {
        deniedTokens.add(token);
    }

    public boolean isDenied(String token) {
        return deniedTokens.contains(token);
    }
}
