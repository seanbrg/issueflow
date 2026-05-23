package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getByProject(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getByProject(projectId));
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<TicketResponse>> getDeleted(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getDeleted(projectId));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.ok(ticketService.create(request));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getById(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getById(ticketId));
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> update(@PathVariable Long ticketId,
                                                 @RequestBody UpdateTicketRequest request) {
        return ResponseEntity.ok(ticketService.update(ticketId, request));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> softDelete(@PathVariable Long ticketId) {
        ticketService.softDelete(ticketId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/restore")
    public ResponseEntity<TicketResponse> restore(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.restore(ticketId));
    }
}
