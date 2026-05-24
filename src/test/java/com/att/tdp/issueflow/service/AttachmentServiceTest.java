package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AttachmentResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock AttachmentRepository attachmentRepository;
    @Mock TicketRepository ticketRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks AttachmentService attachmentService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Ticket buildTicket(Long id) {
        User owner = User.builder().id(1L).username("owner").email("o@x.com")
                .fullName("Owner").role(UserRole.ADMIN).password("h")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
        Project project = Project.builder().id(1L).name("P").owner(owner)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
        return Ticket.builder()
                .id(id).title("T").description("d")
                .status(TicketStatus.TODO).priority(TicketPriority.LOW).type(TicketType.BUG)
                .project(project).overdue(false)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).version(0L).build();
    }

    private Attachment buildAttachment(Long id, Ticket ticket) {
        return Attachment.builder()
                .id(id).ticket(ticket)
                .filename("file.png").contentType("image/png")
                .data(new byte[]{1, 2, 3}).build();
    }

    private MockMultipartFile pngFile(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    private MockMultipartFile fileOf(String contentType, byte[] content) {
        return new MockMultipartFile("file", "upload", contentType, content);
    }

    // ── upload — happy path ───────────────────────────────────────────────────

    @Test
    void upload_validPng_savesAndReturnsResponse() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        MockMultipartFile file = pngFile("screenshot.png", new byte[]{1, 2, 3});
        AttachmentResponse response = attachmentService.upload(1L, file);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTicketId()).isEqualTo(1L);
        assertThat(response.getFilename()).isEqualTo("screenshot.png");
        assertThat(response.getContentType()).isEqualTo("image/png");
        verify(attachmentRepository).save(any());
    }

    @ParameterizedTest(name = "allowed type: {0}")
    @ValueSource(strings = {"image/png", "image/jpeg", "application/pdf", "text/plain"})
    void upload_allAllowedMimeTypes_accepted(String mimeType) {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        MockMultipartFile file = fileOf(mimeType, new byte[]{1});
        AttachmentResponse response = attachmentService.upload(1L, file);

        assertThat(response.getContentType()).isEqualTo(mimeType);
    }

    // ── upload — ticket not found ─────────────────────────────────────────────

    @Test
    void upload_ticketNotFound_throws404() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.upload(99L, pngFile("f.png", new byte[]{1})))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(attachmentRepository, never()).save(any());
    }

    // ── upload — size validation ──────────────────────────────────────────────

    @Test
    void upload_fileTooLarge_throws400() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));

        byte[] oversized = new byte[10 * 1024 * 1024 + 1]; // 10 MB + 1 byte
        MockMultipartFile file = pngFile("big.png", oversized);

        assertThatThrownBy(() -> attachmentService.upload(1L, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("10 MB");

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_exactlyAtLimit_accepted() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        byte[] exactly10Mb = new byte[10 * 1024 * 1024]; // exactly 10 MB
        MockMultipartFile file = pngFile("max.png", exactly10Mb);

        AttachmentResponse response = attachmentService.upload(1L, file);

        assertThat(response).isNotNull();
        verify(attachmentRepository).save(any());
    }

    // ── upload — MIME type validation ─────────────────────────────────────────

    @ParameterizedTest(name = "rejected type: {0}")
    @ValueSource(strings = {"image/gif", "image/webp", "application/json",
                            "application/zip", "video/mp4", "application/octet-stream"})
    void upload_disallowedMimeType_throws400(String mimeType) {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));

        MockMultipartFile file = fileOf(mimeType, new byte[]{1});

        assertThatThrownBy(() -> attachmentService.upload(1L, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_nullContentType_throws400() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));

        MockMultipartFile file = new MockMultipartFile("file", "upload", null, new byte[]{1});

        assertThatThrownBy(() -> attachmentService.upload(1L, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingAttachment_deletesIt() {
        Ticket ticket = buildTicket(1L);
        Attachment attachment = buildAttachment(5L, ticket);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByIdAndTicket_Id(5L, 1L)).thenReturn(Optional.of(attachment));

        attachmentService.delete(1L, 5L);

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void delete_ticketNotFound_throws404() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.delete(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void delete_attachmentNotOnTicket_throws404() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByIdAndTicket_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.delete(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(attachmentRepository, never()).delete(any());
    }
}
