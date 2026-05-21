# AI Prompts Log

## Usual Cursor workflow

- I usually work in **Cursor Premium mode**.
- I usually start with **Plan mode** to shape the task before implementation.
- I provide the task, then ask the agent to execute against the approved plan.
- During execution I track progress by asking the agent to update the **todo list** step-by-step.
- I use this flow to keep the implementation predictable, auditable, and easy to review.


## How AI was used
- Converted assignment requirements into a concrete backend architecture in Spring Boot.
- Generated entities, DTOs, repositories, services, controllers, and JWT security flow.
- Added core business rule enforcement (status transitions, DONE immutability, optimistic-concurrency checks).
- Added extended behaviors (audit logs, dependencies, attachments, CSV import/export, soft-delete restore, mentions, auto-escalation, auto-assignment/workload).
- Added integration tests for core and extended flows, including security and contract-level request/response coverage.
- Produced setup and run documentation for submission artifacts.
- An example is attached uder the name "instructions-example.md".


