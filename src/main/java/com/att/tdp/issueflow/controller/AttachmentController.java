package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.AttachmentResponse;
import com.att.tdp.issueflow.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(attachmentService.upload(ticketId, file));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId) {
        attachmentService.delete(ticketId, attachmentId);
        return ResponseEntity.ok().build();
    }
}
