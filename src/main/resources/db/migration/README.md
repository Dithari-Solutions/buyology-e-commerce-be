# Flyway migrations (`classpath:db/migration`)

This project uses **Flyway in hybrid mode** during the transition off Hibernate's
`ddl-auto=update`:

- `spring.flyway.baseline-on-migrate=true` — on an existing database, Flyway stamps
  it at baseline version `0` and does **not** replay history. On a brand-new database,
  Flyway runs every `V<n>__*.sql` here in order.
- `spring.jpa.hibernate.ddl-auto=update` is still the schema authority **for now**, so
  Hibernate continues to add new entity columns automatically. Flyway is the framework
  for *deliberate* schema/data changes (constraints, backfills, indexes, data fixes).

## Adding a migration

Create a file named `V<n>__short_description.sql` (incrementing `<n>`), e.g.:

```
V1__backfill_game_results_play_date.sql
V2__add_promo_usage_unique_constraint.sql
```

Use idempotent / guarded DDL where possible (`IF NOT EXISTS`, `WHERE NOT EXISTS`) so a
migration is safe even if Hibernate's `update` already applied part of the change.

## Cutover to `validate` (later, with DB access)

When ready to make Flyway the sole authority:
1. Generate a full baseline (`V1__baseline.sql`) from the **real** production schema
   (`pg_dump --schema-only`), verify it on a staging clone.
2. Set `spring.jpa.hibernate.ddl-auto=validate`.
3. Keep `baseline-on-migrate=true` so existing prod DBs are stamped, not rebuilt.

The pre-Flyway manual scripts in `../` (date-named `.sql` files) were applied by hand and
are superseded by this directory going forward.
