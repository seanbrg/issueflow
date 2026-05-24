package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.Attachment;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentResponse {

    private Long id;
    private Long ticketId;
    private String filename;
    private String contentType;

    public static AttachmentResponse from(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .ticketId(attachment.getTicket().getId())
                .filename(attachment.getFilename())
                .contentType(attachment.getContentType())
                .build();
    }
}
