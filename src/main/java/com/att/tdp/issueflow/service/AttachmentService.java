package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AttachmentResponse;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class AttachmentService {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "application/pdf", "text/plain");

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public AttachmentResponse upload(Long ticketId, MultipartFile file) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        validate(file);

        try {
            Attachment attachment = Attachment.builder()
                    .ticket(ticket)
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .data(file.getBytes())
                    .build();
            Attachment saved = attachmentRepository.save(attachment);
            auditLogService.log("UPLOAD_ATTACHMENT", "ATTACHMENT", saved.getId(), ActorType.USER);
            return AttachmentResponse.from(saved);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    public void delete(Long ticketId, Long attachmentId) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Attachment attachment = attachmentRepository.findByIdAndTicket_Id(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found: " + attachmentId + " on ticket: " + ticketId));
        attachmentRepository.delete(attachment);
        auditLogService.log("DELETE_ATTACHMENT", "ATTACHMENT", attachmentId, ActorType.USER);
    }

    // ── validation ────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException(
                    "File size " + file.getSize() + " bytes exceeds the 10 MB limit");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct)) {
            throw new BadRequestException(
                    "Unsupported file type: '" + ct + "'. Allowed: image/png, image/jpeg, application/pdf, text/plain");
        }
    }
}
