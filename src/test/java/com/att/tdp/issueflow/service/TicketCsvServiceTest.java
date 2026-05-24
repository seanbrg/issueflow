package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.CsvImportResult;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketCsvServiceTest {

    @Mock TicketService ticketService;
    @Mock TicketRepository ticketRepository;
    @Mock ProjectRepository projectRepository;

    private TicketCsvService csvService;

    private static final Long PROJECT_ID = 1L;

    @BeforeEach
    void setUp() {
        csvService = new TicketCsvService(ticketService, ticketRepository, projectRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Project buildProject() {
        User owner = User.builder().id(1L).username("owner").email("o@x.com")
                .fullName("Owner").role(UserRole.ADMIN).password("h")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
        return Project.builder().id(PROJECT_ID).name("P1").owner(owner)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
    }

    private Ticket buildTicket(Long id, String title, TicketPriority priority, Long assigneeId) {
        Project project = buildProject();
        User assignee = assigneeId == null ? null :
                User.builder().id(assigneeId).username("dev").email("d@x.com")
                        .fullName("Dev").role(UserRole.DEVELOPER).password("h")
                        .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
        return Ticket.builder()
                .id(id).title(title).description("desc")
                .status(TicketStatus.TODO).priority(priority).type(TicketType.BUG)
                .project(project).assignee(assignee).overdue(false)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).version(0L).build();
    }

    private TicketResponse stubTicketResponse(Long id) {
        return TicketResponse.builder()
                .id(id).title("T").status("TODO").priority("LOW").type("BUG")
                .projectId(PROJECT_ID).overdue(false).version(0L).build();
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "tickets.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private void stubProject() {
        when(projectRepository.findByIdAndDeletedAtIsNull(PROJECT_ID))
                .thenReturn(Optional.of(buildProject()));
    }

    // ── export ────────────────────────────────────────────────────────────────

    @Test
    void export_writesHeaderAndOneRow() throws IOException {
        stubProject();
        Ticket t = buildTicket(42L, "Fix login", TicketPriority.HIGH, 7L);
        when(ticketRepository.findAllByProject_IdAndDeletedAtIsNull(PROJECT_ID))
                .thenReturn(List.of(t));

        byte[] csv = csvService.export(PROJECT_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(content))) {

            List<CSVRecord> records = parser.getRecords();
            assertThat(records).hasSize(1);
            CSVRecord row = records.get(0);
            assertThat(row.get("id")).isEqualTo("42");
            assertThat(row.get("title")).isEqualTo("Fix login");
            assertThat(row.get("status")).isEqualTo("TODO");
            assertThat(row.get("priority")).isEqualTo("HIGH");
            assertThat(row.get("type")).isEqualTo("BUG");
            assertThat(row.get("assigneeId")).isEqualTo("7");
        }
    }

    @Test
    void export_ticketWithoutAssignee_assigneeIdIsEmpty() throws IOException {
        stubProject();
        Ticket t = buildTicket(1L, "No assignee", TicketPriority.LOW, null);
        when(ticketRepository.findAllByProject_IdAndDeletedAtIsNull(PROJECT_ID))
                .thenReturn(List.of(t));

        byte[] csv = csvService.export(PROJECT_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(content))) {
            assertThat(parser.getRecords().get(0).get("assigneeId")).isEmpty();
        }
    }

    @Test
    void export_titleWithComma_isCorrectlyQuoted() throws IOException {
        stubProject();
        Ticket t = buildTicket(1L, "Fix, the bug", TicketPriority.LOW, null);
        when(ticketRepository.findAllByProject_IdAndDeletedAtIsNull(PROJECT_ID))
                .thenReturn(List.of(t));

        byte[] csv = csvService.export(PROJECT_ID);
        String content = new String(csv, StandardCharsets.UTF_8);

        // Round-trip: parse back and verify field value is intact
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(content))) {
            assertThat(parser.getRecords().get(0).get("title")).isEqualTo("Fix, the bug");
        }
    }

    // ── import — happy path ───────────────────────────────────────────────────

    @Test
    void importCsv_validRows_allCreated() {
        stubProject();
        when(ticketService.create(any())).thenReturn(stubTicketResponse(10L));

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,First ticket,Some desc,TODO,LOW,BUG,\n" +
                     "2,Second ticket,More desc,IN_PROGRESS,HIGH,FEATURE,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getErrors()).isEmpty();
        verify(ticketService, times(2)).create(any());
    }

    @Test
    void importCsv_titleWithComma_parsedCorrectly() {
        stubProject();
        ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        when(ticketService.create(captor.capture())).thenReturn(stubTicketResponse(10L));

        // Comma inside field — Commons CSV RFC 4180 quoting
        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,\"Title, with comma\",desc,TODO,LOW,BUG,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(captor.getValue().getTitle()).isEqualTo("Title, with comma");
    }

    @Test
    void importCsv_titleWithEscapedQuotes_parsedCorrectly() {
        stubProject();
        ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        when(ticketService.create(captor.capture())).thenReturn(stubTicketResponse(10L));

        // Double-quoted quote: ""word"" → "word"
        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,\"Title with \"\"quotes\"\"\",desc,TODO,LOW,BUG,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(captor.getValue().getTitle()).isEqualTo("Title with \"quotes\"");
    }

    @Test
    void importCsv_noAssigneeId_ticketCreatedWithNullAssignee() {
        stubProject();
        ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        when(ticketService.create(captor.capture())).thenReturn(stubTicketResponse(10L));

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,Ticket,desc,TODO,LOW,BUG,\n";

        csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(captor.getValue().getAssigneeId()).isNull();
    }

    @Test
    void importCsv_withAssigneeId_parsedAsLong() {
        stubProject();
        ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        when(ticketService.create(captor.capture())).thenReturn(stubTicketResponse(10L));

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,Ticket,desc,TODO,LOW,BUG,42\n";

        csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(captor.getValue().getAssigneeId()).isEqualTo(42L);
    }

    // ── import — error cases ──────────────────────────────────────────────────

    @Test
    void importCsv_blankTitle_rowFails() {
        stubProject();

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,,desc,TODO,LOW,BUG,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getCreated()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("title is required"));
        verify(ticketService, never()).create(any());
    }

    @Test
    void importCsv_invalidStatus_rowFails() {
        stubProject();

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,Ticket,desc,NOT_A_STATUS,LOW,BUG,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0)).contains("invalid status").contains("NOT_A_STATUS");
        verify(ticketService, never()).create(any());
    }

    @Test
    void importCsv_invalidPriority_rowFails() {
        stubProject();

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,Ticket,desc,TODO,URGENT,BUG,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0)).contains("invalid priority").contains("URGENT");
    }

    @Test
    void importCsv_unknownAssignee_rowFails() {
        stubProject();
        when(ticketService.create(any()))
                .thenThrow(new ResourceNotFoundException("User not found: 99"));

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     "1,Ticket,desc,TODO,LOW,BUG,99\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0)).contains("User not found: 99");
    }

    // ── import — mixed batch ──────────────────────────────────────────────────

    @Test
    void importCsv_mixedRows_countsCorrect() {
        stubProject();
        when(ticketService.create(any())).thenReturn(stubTicketResponse(10L));

        String csv = "id,title,description,status,priority,type,assigneeId\n" +
                     // row 2 — valid
                     "1,Valid ticket,desc,TODO,LOW,BUG,\n" +
                     // row 3 — blank title
                     "2,,desc,TODO,LOW,BUG,\n" +
                     // row 4 — valid with quoted comma
                     "3,\"Another, ticket\",desc,IN_PROGRESS,HIGH,FEATURE,\n" +
                     // row 5 — invalid enum
                     "4,Bad ticket,desc,MAYBE,LOW,BUG,\n" +
                     // row 6 — valid
                     "5,Last ticket,,DONE,CRITICAL,TECHNICAL,\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getCreated()).isEqualTo(3);
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Row 3") && e.contains("title is required"));
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Row 5") && e.contains("MAYBE"));
    }

    @Test
    void importCsv_emptyFile_returnsZeroCounts() {
        stubProject();

        String csv = "id,title,description,status,priority,type,assigneeId\n";

        CsvImportResult result = csvService.importCsv(csvFile(csv), PROJECT_ID);

        assertThat(result.getCreated()).isZero();
        assertThat(result.getFailed()).isZero();
        assertThat(result.getErrors()).isEmpty();
        verify(ticketService, never()).create(any());
    }
}
