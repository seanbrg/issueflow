package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.CsvImportResult;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketCsvService {

    static final String[] CSV_HEADERS = {"id", "title", "description", "status", "priority", "type", "assigneeId"};

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;

    // ── Export ────────────────────────────────────────────────────────────────

    @Transactional
    public byte[] export(Long projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        List<Ticket> tickets = ticketRepository.findAllByProject_IdAndDeletedAtIsNull(projectId);

        try (StringWriter sw = new StringWriter();
             CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                     .setHeader(CSV_HEADERS)
                     .build())) {

            for (Ticket t : tickets) {
                printer.printRecord(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription(),
                        t.getStatus().name(),
                        t.getPriority().name(),
                        t.getType().name(),
                        t.getAssignee() != null ? t.getAssignee().getId() : "");
            }
            return sw.toString().getBytes(StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV export", e);
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    public CsvImportResult importCsv(MultipartFile file, Long projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        int created = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                String error = processRow(record, projectId, rowNum);
                if (error == null) {
                    created++;
                } else {
                    failed++;
                    errors.add(error);
                }
            }

        } catch (IOException e) {
            throw new BadRequestException("Failed to parse CSV: " + e.getMessage());
        }

        return new CsvImportResult(created, failed, errors);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Returns null on success or an error message on failure. */
    private String processRow(CSVRecord record, Long projectId, int rowNum) {
        try {
            String title = record.get("title");
            if (title == null || title.isBlank()) {
                return "Row " + rowNum + ": title is required";
            }

            TicketStatus status   = parseEnum(TicketStatus.class,   record.get("status"),   "status");
            TicketPriority priority = parseEnum(TicketPriority.class, record.get("priority"), "priority");
            TicketType type       = parseEnum(TicketType.class,     record.get("type"),     "type");

            String assigneeRaw = record.get("assigneeId");
            Long assigneeId = (assigneeRaw != null && !assigneeRaw.isBlank())
                    ? Long.parseLong(assigneeRaw.trim()) : null;

            CreateTicketRequest req = new CreateTicketRequest();
            req.setTitle(title.trim());
            req.setDescription(record.get("description"));
            req.setStatus(status);
            req.setPriority(priority);
            req.setType(type);
            req.setProjectId(projectId);
            req.setAssigneeId(assigneeId);

            ticketService.create(req);
            return null;

        } catch (IllegalArgumentException e) {
            return "Row " + rowNum + ": " + e.getMessage();
        } catch (ResourceNotFoundException e) {
            return "Row " + rowNum + ": " + e.getMessage();
        } catch (Exception e) {
            return "Row " + rowNum + ": " + e.getMessage();
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + field + ": '" + value.trim() + "'");
        }
    }
}
