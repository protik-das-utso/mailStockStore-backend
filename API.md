# API surface (v1)

Auth (`/api/auth/*`, rate-limited)
  POST  /register              { email, password, fullName, role: SELLER|BUYER }
  POST  /verify-email          { token }
  POST  /login                 { email, password }
  POST  /refresh               { token }
  POST  /logout                { token }
  POST  /forgot-password       { email }
  POST  /reset-password        { token, newPassword }

Profile (any authenticated)
  GET   /api/profile/me
  PATCH /api/profile/me                       { fullName?, phone? }
  POST  /api/profile/change-password          { currentPassword, newPassword }

Seller
  GET   /api/seller/dashboard
  POST  /api/submissions                      { emailAddress, emailPassword, twoFactorCode?, accountType: OLD|NEW, country, warrantyDays?, recoveryEmail?, phoneNumber?, accountCreationYear?, phoneVerified?, recoveryEmailAdded?, quantity?, additionalInfo?, notes? }
  POST  /api/submissions/bulk                 { items: [ <submission>, ... ] }   # add multiple Gmail accounts at once
  GET   /api/submissions/me
  GET   /api/submissions/{id}
  PUT   /api/submissions/{id}                  # modify & resubmit a PENDING/NEEDS_MODIFY submission (same body as POST)
  POST  /api/submissions/{id}/counter-response { accept: boolean }
  GET   /api/wallet/me
  GET   /api/wallet/me/transactions
  POST  /api/wallet/withdraw                  { amount, destination }
  GET   /api/wallet/withdraw/me

Buyer
  GET   /api/buyer/dashboard
  GET   /api/inventory/browse?category&q      (public; email masked, no credentials)
  GET   /api/inventory/browse/featured        (public; masked)
  GET   /api/inventory/browse/{id}            (public; masked)
  POST  /api/orders                           { inventoryIds:[..], couponCode? }
  GET   /api/orders/me
  GET   /api/orders/{id}                       # buying is instant: POST /api/orders debits wallet balance and delivers
  GET   /api/orders/me/emails                  # purchased-email vault: delivered items w/ full credentials
  POST  /api/warranty                         { orderItemId, reason, description, evidenceUrl }
  GET   /api/warranty/me
  POST  /api/reviews                          { inventoryId, rating(1-5), body }

Support (any authenticated)
  POST  /api/support                          { subject, category, body, attachmentUrl }
  GET   /api/support/me
  GET   /api/support/{id}
  GET   /api/support/{id}/messages
  POST  /api/support/{id}/reply               { body, attachmentUrl }
  POST  /api/support/{id}/close

Notifications
  GET   /api/notifications
  GET   /api/notifications/unread-count
  POST  /api/notifications/{id}/read
  POST  /api/notifications/read-all

Public
  GET   /api/public/announcements
  GET   /api/public/settings
  GET   /api/public/reviews/latest
  GET   /api/public/reviews/product/{inventoryId}

Admin (ROLE_ADMIN)
  GET   /api/admin/dashboard
  GET   /api/admin/users?role=SELLER|BUYER|ADMIN
  POST  /api/admin/users/{id}/lock?locked=
  POST  /api/admin/users/{id}/enable?enabled=
  GET   /api/submissions/admin?status=
  POST  /api/submissions/admin/{id}/review    { action: APPROVE|REJECT|COUNTER|NEEDS_MODIFY, purchasePrice?, sellingPrice?, counterPrice?, reviewTag?, adminNote?, deliveryPayload?, internalNotes? }
  GET   /api/admin/pricing                     # per-type payout/sell price + stock availability vs target
  PUT   /api/admin/pricing                     [ { key: old|new, payoutPrice?, sellPrice?, targetStock? } ]
  GET   /api/inventory/admin?status=
  PATCH /api/inventory/admin/{id}
  GET   /api/orders/admin?status=
  POST  /api/orders/admin/manual-deliver       { userId, inventoryId, chargeBalance, note }  # warranty/replacement
  POST  /api/wallet/admin/users/{userId}/credit { amount, note }                              # add balance to any user
  GET   /api/wallet/admin/withdrawals?status=
  POST  /api/wallet/admin/withdrawals/{id}/decision { approve, adminNote, payoutTxid }
  GET   /api/warranty/admin?status=
  POST  /api/warranty/admin/{id}/decision     { resolution: REPLACE|REFUND|REJECT|CLOSE, adminNote }
  GET   /api/support/admin?status=
  GET   /api/coupons/admin  POST/PUT/DELETE
  GET/POST/PUT/DELETE /api/announcements/admin
  GET/PUT /api/settings/admin
  GET   /api/audit-logs
  GET   /api/reports/daily-revenue
  GET   /api/reports/monthly-revenue
  GET   /api/reports/profit
  GET   /api/reports/inventory-stats
  GET   /api/reports/warranty-stats
  GET   /api/reviews/admin/pending
  POST  /api/reviews/admin/{id}/approve?approve=

Swagger UI: /swagger-ui.html
