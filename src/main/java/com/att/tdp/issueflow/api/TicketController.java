package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.AttachmentResponse;
import com.att.tdp.issueflow.api.dto.DeletedTicketResponse;
import com.att.tdp.issueflow.api.dto.TicketCreateRequest;
import com.att.tdp.issueflow.api.dto.TicketDependencyCreateRequest;
import com.att.tdp.issueflow.api.dto.TicketDependencyResponse;
import com.att.tdp.issueflow.api.dto.TicketImportResponse;
import com.att.tdp.issueflow.api.dto.TicketResponse;
import com.att.tdp.issueflow.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.service.AttachmentService;
import com.att.tdp.issueflow.service.TicketService;
import com.att.tdp.issueflow.service.TicketDependencyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;
    private final TicketDependencyService ticketDependencyService;
    private final AttachmentService attachmentService;

    @GetMapping
    public List<TicketResponse> getTickets(@RequestParam Long projectId) {
        return ticketService.getByProject(projectId);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse getTicketById(@PathVariable Long ticketId) {
        return ticketService.getById(ticketId);
    }

    @PostMapping
    public TicketResponse createTicket(@Valid @RequestBody TicketCreateRequest request) {
        return ticketService.create(request);
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<Void> updateTicket(@PathVariable Long ticketId, @Valid @RequestBody TicketUpdateRequest request) {
        ticketService.update(ticketId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long ticketId) {
        ticketService.softDelete(ticketId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DeletedTicketResponse> getDeletedTickets(@RequestParam Long projectId) {
        return ticketService.getDeletedByProject(projectId);
    }

    @PostMapping("/{ticketId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreTicket(@PathVariable Long ticketId) {
        ticketService.restore(ticketId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/dependencies")
    public ResponseEntity<Void> addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody TicketDependencyCreateRequest request
    ) {
        ticketDependencyService.addDependency(ticketId, request.blockedBy());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{ticketId}/dependencies")
    public List<TicketDependencyResponse> getDependencies(@PathVariable Long ticketId) {
        return ticketDependencyService.listDependencies(ticketId);
    }

    @DeleteMapping("/{ticketId}/dependencies/{blockerId}")
    public ResponseEntity<Void> removeDependency(@PathVariable Long ticketId, @PathVariable Long blockerId) {
        ticketDependencyService.removeDependency(ticketId, blockerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/attachments")
    public AttachmentResponse uploadAttachment(@PathVariable Long ticketId, @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(ticketId, file);
    }

    @DeleteMapping("/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long ticketId, @PathVariable Long attachmentId) {
        attachmentService.delete(ticketId, attachmentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportTickets(@RequestParam Long projectId) {
        String content = ticketService.exportCsv(projectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tickets-project-" + projectId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TicketImportResponse importTickets(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long projectId
    ) {
        return ticketService.importCsv(projectId, file);
    }
}


