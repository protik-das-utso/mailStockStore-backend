# MailStock.store — Backend (Spring Boot)

Reseller marketplace for digital assets. Sellers submit → Admin reviews → Inventory → Buyer purchases. Seller and buyer never communicate.

## Stack
- Java 21, Spring Boot 3.3
- Spring Security + JWT (access + refresh)
- Spring Data JPA + Flyway (PostgreSQL)
- Spring Data Redis (cache, rate limiting)
- Spring Mail (SMTP) + Thymeleaf email templates
- Bucket4j rate limiting
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)

## Run locally

1. Copy env: `cp .env.example .env` and fill values.
2. Start Postgres + Redis (your own docker-compose).
3. Export env vars, then: `mvn spring-boot:run`
4. Swagger: http://localhost:8080/swagger-ui.html

## Modules
- `auth` — register / verify / login / refresh / forgot / reset
- `submission` — seller submits assets, admin approves/rejects/counters
- `inventory` — approved items, buyer browse
- `order` + `payment` — Binance manual TXID flow
- `wallet` — seller balances + withdrawals
- `warranty` — buyer claims
- `support` — tickets + messages
- `notification`, `coupon`, `announcement`, `setting`, `audit`, `report`, `admin`

## Roles
`ADMIN`, `SELLER`, `BUYER` — enforced via `@PreAuthorize`.

## Bootstrap admin
Seed migration creates `admin@mailstock.store` / `ChangeMe!123` — change immediately.
