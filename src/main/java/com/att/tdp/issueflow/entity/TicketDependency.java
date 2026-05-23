package com.att.tdp.issueflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ticket_dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class TicketDependency {

    @EmbeddedId
    private TicketDependencyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("ticketId")
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("blockedById")
    @JoinColumn(name = "blocked_by_id")
    private Ticket blockedBy;
}
