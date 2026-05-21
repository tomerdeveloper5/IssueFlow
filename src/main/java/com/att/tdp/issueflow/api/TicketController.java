package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.TicketCreateRequest;
import com.att.tdp.issueflow.api.dto.TicketResponse;
import com.att.tdp.issueflow.api.dto.TicketUpdateRequest;
import com.att.tdp.issueflow.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;

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
}


