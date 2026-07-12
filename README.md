# MailStock.store — Backend (Spring Boot)

Reseller marketplace for digital assets. Sellers submit → Admin reviews → Inventory → Buyer purchases. Seller and buyer never communicate.

## Stack
- Java 21, Spring Boot 3.3
- Spring Security + JWT (access + refresh)
- Spring Data JPA + Flyway (PostgreSQL)
- Spring Mail (SMTP) + Thymeleaf email templates
- Bucket4j rate limiting (in-memory, per instance)
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)

## Run locally

1. Copy env: `cp .env.example .env` and fill values.
2. Start Postgres (your own docker-compose).
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
The seed migration creates `admin@mailstock.store` with a default password. **That password is
public** (it was committed to this repo's history before this fix) — do not treat it as a secret.
The account is created with `must_change_password = TRUE`, so the backend hard-blocks every request
from it except `POST /api/profile/change-password` (and reading `/api/profile/me`, and logout) until
a new password is set. Log in once and change the password immediately; until you do, this account
can do nothing else even though the old password is known.
