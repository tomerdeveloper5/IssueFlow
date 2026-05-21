package com.att.tdp.issueflow.domain;

public enum TicketStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE;

    public boolean canTransitionTo(TicketStatus next) {
        return switch (this) {
            case TODO -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == IN_REVIEW;
            case IN_REVIEW -> next == DONE;
            case DONE -> false;
        };
    }
}


