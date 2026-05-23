package com.att.tdp.issueflow.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TicketDependencyId implements Serializable {

    private Long ticketId;
    private Long blockedById;
}
