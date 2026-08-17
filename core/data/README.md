# core:data

Repositories bridging `core:network` and the Room read-cache (app-private, backup-excluded,
TTL-bound, wiped on logout), org-unit context holder (active pin → outbound header),
optimistic-locking helpers (version echo, 409 mapping). No offline writes by design.
