---
title: Your first change
description: Add a field to the vehicle entity, end to end, following the layer rules.
---

This tutorial walks a small, realistic change through **every layer**. We add a `color` field to
vehicles and surface it in the UI. It is deliberately boring: the point is the *path*, not the field.

:::tip
The same path applies to any new field or entity. The order matters: **database first**, because the
database is the source of truth.
:::

## 0. Create a branch

```bash
git switch -c feat/vehicle-color
```

## 1. Change the schema

**`db/init/01-tables.sql`** — add the column to the `vehicles` table:

```sql
  vehicle_type   VARCHAR(50),
  color          VARCHAR(30),
  load_capacity  DECIMAL(10,1) CHECK (load_capacity IS NULL OR load_capacity >= 0),
```

**`db/init/05-views.sql`** — expose it in the read projection:

```sql
CREATE OR REPLACE VIEW v_vehicle AS
SELECT id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
       color, load_capacity, mileage, status
FROM vehicles;
```

**`db/init/30-fleet.sql`** — teach the save procedure about it (new `IN` parameter, and include it
in both the `INSERT` and the `UPDATE`):

```sql
CREATE PROCEDURE sp_vehicle_save(IN p_id BIGINT, IN p_code VARCHAR(30), IN p_plates VARCHAR(20),
    IN p_brand VARCHAR(50), IN p_model VARCHAR(50), IN p_year INT, IN p_serial VARCHAR(60),
    IN p_type VARCHAR(50), IN p_color VARCHAR(30), IN p_capacity DECIMAL(10,1),
    IN p_mileage DECIMAL(10,1), OUT p_new_id BIGINT, OUT p_problems TEXT)
```

```sql
    INSERT INTO vehicles (id, internal_code, plates, brand, model, year, serial_number,
                          vehicle_type, color, load_capacity, mileage, status)
    VALUES (p_new_id, p_code, p_plates, p_brand, p_model, p_year, p_serial, p_type,
            p_color, p_capacity, COALESCE(p_mileage, 0), 'available');
```

```sql
    UPDATE vehicles SET internal_code = p_code, plates = p_plates, brand = p_brand,
                        model = p_model, year = p_year, serial_number = p_serial,
                        vehicle_type = p_type, color = p_color, load_capacity = p_capacity,
                        mileage = GREATEST(mileage, COALESCE(p_mileage, mileage))
    WHERE id = p_id;
```

## 2. Apply it to the database

```bash
db/build-bootstrap.sh                  # regenerate SYSPORT_MARJAN.sql (committed)
docker compose down -v && docker compose up -d
```

:::caution
`db/init/*.sql` only run on an empty volume. During development, reset with `down -v`.
:::

## 3. Update the record

**`Vehicle.java`** — records are immutable; add the field in the position that matches the view
column order (so the mapping stays readable):

```java
public record Vehicle(
        long id, String internalCode, String plates, String brand, String model,
        Integer year, String serialNumber, String vehicleType, String color,
        BigDecimal loadCapacity, BigDecimal mileage, VehicleStatus status) {
```

Update `empty()` so a blank draft still compiles:

```java
return new Vehicle(0, "", "", "", "", null, "", "", "", BigDecimal.ZERO, BigDecimal.ZERO, AVAILABLE);
```

## 4. Update the repository

**`VehicleRepository.java`** — read the column in `map(...)` and pass it in `save(...)`:

```java
        rs.getString("vehicle_type"),
        rs.getString("color"),
        rs.getBigDecimal("load_capacity"),
```

```java
    public Result<Long> save(Vehicle vehicle) {
        return Database.callForId("{call sp_vehicle_save(?,?,?,?,?,?,?,?,?,?,?,?,?)}",
                vehicle.id(), vehicle.internalCode(), vehicle.plates(), vehicle.brand(),
                vehicle.model(), vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                vehicle.color(), vehicle.loadCapacity(), vehicle.mileage());
    }
```

:::danger
The number of `?` placeholders must equal **IN parameters + OUT parameters**. `sp_vehicle_save`
now has 11 `IN` + 2 `OUT` = **13**, hence the extra `?` and the extra argument. A mismatch throws
at runtime, not at compile time — this is the classic bug in this codebase.
:::

## 5. The service needs nothing

`VehicleService.save` only checks the `fleet.write` permission and delegates. Rules that can live
in SQL should; the service stays thin.

## 6. Update the view

**`VehiclesView.java`** — add a table column and a form field:

```java
        RecordTable.Column.of("Color", Vehicle::color),
```

```java
        .addText("color", "Color", vehicle.color(), "Color de la unidad (opcional)")
```

and pass it when building the record in the form handler:

```java
        Vehicle built = new Vehicle(vehicle.id(), form.text("code"), form.text("plates"),
                form.text("brand"), form.text("model"), year, form.text("serial"),
                form.text("type"), form.text("color"), capacity, mileage, vehicle.status());
```

## 7. Test it

A pure data change like this is covered by the existing integration suite; if you were adding a
**rule**, add a test in the same commit. See [Testing](/start/testing/) and
`src/test/java/mx/marjan/rules/SqlRulesTest.java`.

## 8. Commit (schema separate from Java)

```bash
git add db/ SYSPORT_MARJAN.sql
git commit -m "feat(db): add color to vehicles"

git add src/
git commit -m "feat(fleet): show and edit vehicle color"
```

The [Git workflow](/architecture/conventions/#commits) explains why schema changes are their own
commit.

## Checklist

- [ ] Schema changed and `SYSPORT_MARJAN.sql` regenerated
- [ ] Database reset so the change is live
- [ ] Record, repository, view updated (service only if permission/rules changed)
- [ ] Placeholder count matches `IN + OUT`
- [ ] Compiles and runs; manual smoke test through the UI
- [ ] Atomic commits with conventional messages
