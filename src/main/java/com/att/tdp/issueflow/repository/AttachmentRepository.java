package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findAllByTicket_Id(Long ticketId);

    // Validates ownership before deleting (DELETE /tickets/:id/attachments/:attachmentId)
    Optional<Attachment> findByIdAndTicket_Id(Long id, Long ticketId);
}
