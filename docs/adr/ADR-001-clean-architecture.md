# ADR-001 Clean Architecture

Status: Accepted

## Context

FinLedger is a transactional system that must support auditing, fraud detection, and transaction processing.

## Decision

Adopt Clean Architecture with the following distinct layers:

- Presentation
- Application
- Domain
- Infrastructure

## Consequences

- High Testability: Business logic can be tested in isolation without external dependencies or frameworks
- Low Coupling: High independence between UI, database, frameworks, and core domain logic
- Increased Boilerplate: Requires more classes, interfaces, and explicit mappers between layers.
