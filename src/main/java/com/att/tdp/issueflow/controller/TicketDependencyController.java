package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.AddDependencyRequest;
import com.att.tdp.issueflow.dto.TicketDependencySummaryResponse;
import com.att.tdp.issueflow.service.TicketDependencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
@RequiredArgsConstructor
public class TicketDependencyController {

    private final TicketDependencyService dependencyService;

    @PostMapping
    public ResponseEntity<TicketDependencySummaryResponse> addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddDependencyRequest request) {
        return ResponseEntity.ok(dependencyService.addDependency(ticketId, request.getBlockedBy()));
    }

    @GetMapping
    public ResponseEntity<List<TicketDependencySummaryResponse>> getDependencies(@PathVariable Long ticketId) {
        return ResponseEntity.ok(dependencyService.getDependencies(ticketId));
    }

    @DeleteMapping("/{blockerId}")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long ticketId,
            @PathVariable Long blockerId) {
        dependencyService.removeDependency(ticketId, blockerId);
        return ResponseEntity.ok().build();
    }
}
