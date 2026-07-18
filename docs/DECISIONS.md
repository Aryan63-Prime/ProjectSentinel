# Engineering Decisions

Decision 001

Gateway owns WebSocket lifecycle.

Reason

Avoid business logic inside transport layer.

---

Decision 002

Services depend on interfaces.

Reason

Allow Redis/PostgreSQL replacement.

---

Decision 003

Audio is binary only.

Reason

Avoid Base64 overhead.

---

Decision 004

Redis stores latest location only.

Reason

Realtime data belongs in Redis.

History belongs in PostgreSQL.

---

Decision 005

Session owns its state.

Reason

Avoid race conditions.