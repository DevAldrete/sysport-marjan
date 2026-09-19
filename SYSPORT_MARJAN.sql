-- This SQL may need modifications specially for the comments and other details.
-- This is a demo of what could be the database for this project
CREATE TABLE `users` (
  `id` int PRIMARY KEY,
  `employee_id` int UNIQUE,
  `username` varchar(255) UNIQUE NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role_id` int NOT NULL,
  `status` varchar(255) NOT NULL COMMENT 'active, disabled',
  `created_at` timestamp
);

CREATE TABLE `roles` (
  `id` int PRIMARY KEY,
  `name` varchar(255) UNIQUE NOT NULL
);

CREATE TABLE `permissions` (
  `id` int PRIMARY KEY,
  `name` varchar(255) UNIQUE NOT NULL
);

CREATE TABLE `role_permissions` (
  `id` int PRIMARY KEY,
  `role_id` int NOT NULL,
  `permission_id` int NOT NULL
);

CREATE TABLE `employees` (
  `id` int PRIMARY KEY,
  `name` varchar(255) NOT NULL,
  `address` varchar(255),
  `phone` varchar(255) UNIQUE NOT NULL,
  `email` varchar(255) UNIQUE,
  `rfc` varchar(255) UNIQUE,
  `curp` varchar(255) UNIQUE,
  `emergency_contact_name` varchar(255),
  `emergency_contact_phone` varchar(255),
  `license_id` int UNIQUE,
  `status` varchar(255) NOT NULL COMMENT 'available, on_trip, resting, vacation, disabled, terminated',
  `created_at` timestamp
);

CREATE TABLE `licenses` (
  `id` int PRIMARY KEY,
  `license_number` varchar(255) UNIQUE NOT NULL,
  `license_type` varchar(255) NOT NULL,
  `issue_date` date,
  `expiration_date` date NOT NULL,
  `created_at` timestamp,
  `updated_at` timestamp
);

CREATE TABLE `clients` (
  `id` int PRIMARY KEY,
  `name` varchar(255) NOT NULL,
  `rfc` varchar(255) UNIQUE NOT NULL,
  `address` varchar(255),
  `phone` varchar(255),
  `email` varchar(255),
  `client_type` varchar(255) COMMENT 'occasional, frequent',
  `payment_terms` varchar(255) COMMENT 'cash, credit',
  `created_at` timestamp
);

CREATE TABLE `client_rates` (
  `id` int PRIMARY KEY,
  `client_id` int NOT NULL,
  `route_id` int,
  `rate` decimal NOT NULL,
  `valid_from` date,
  `valid_to` date,
  `created_at` timestamp
);

CREATE TABLE `routes` (
  `id` int PRIMARY KEY,
  `origin` varchar(255) NOT NULL,
  `destination` varchar(255) NOT NULL,
  `estimated_km` decimal,
  `description` varchar(255),
  `created_at` timestamp
);

CREATE TABLE `service_requests` (
  `id` int PRIMARY KEY,
  `folio` varchar(255) UNIQUE NOT NULL,
  `client_id` int NOT NULL,
  `route_id` int,
  `cargo_description` varchar(255),
  `estimated_weight` decimal,
  `pickup_date_scheduled` timestamp,
  `delivery_date_scheduled` timestamp,
  `agreed_rate` decimal COMMENT 'snapshot of the price at authorization time; must not change if general tariffs change later',
  `status` varchar(255) NOT NULL COMMENT 'solicitada, autorizada, programada, asignada, en_transito, entregada, cancelada, cerrada',
  `notes` varchar(255),
  `created_by` int,
  `created_at` timestamp
);

CREATE TABLE `vehicles` (
  `id` int PRIMARY KEY,
  `internal_code` varchar(255) UNIQUE NOT NULL,
  `plates` varchar(255) UNIQUE NOT NULL,
  `brand` varchar(255),
  `model` varchar(255),
  `year` int,
  `serial_number` varchar(255) UNIQUE,
  `vehicle_type` varchar(255),
  `load_capacity` decimal,
  `mileage` decimal,
  `status` varchar(255) NOT NULL COMMENT 'available, assigned, on_trip, maintenance, out_of_service, decommissioned',
  `created_at` timestamp
);

CREATE TABLE `trips` (
  `id` int PRIMARY KEY,
  `service_request_id` int NOT NULL COMMENT 'one trip per request in this model',
  `vehicle_id` int NOT NULL,
  `employee_id` int NOT NULL,
  `estimated_km` decimal,
  `actual_km` decimal,
  `departure_datetime` timestamp,
  `arrival_datetime` timestamp,
  `status` varchar(255) NOT NULL,
  `created_by` int,
  `created_at` timestamp
);

CREATE TABLE `expenses` (
  `id` int PRIMARY KEY,
  `trip_id` int NOT NULL,
  `expense_type` varchar(255) NOT NULL COMMENT 'fuel, tolls, food, parking, lodging, repairs, handling, permits, other',
  `amount` decimal NOT NULL,
  `expense_date` date,
  `description` varchar(255),
  `created_at` timestamp
);

CREATE TABLE `advances` (
  `id` int PRIMARY KEY,
  `trip_id` int NOT NULL,
  `employee_id` int NOT NULL,
  `amount_given` decimal NOT NULL,
  `delivered_date` date,
  `status` varchar(255) NOT NULL COMMENT 'pending, settled',
  `created_at` timestamp
);

CREATE TABLE `fuel_loads` (
  `id` int PRIMARY KEY,
  `vehicle_id` int NOT NULL,
  `trip_id` int,
  `fuel_station` varchar(255),
  `load_date` timestamp,
  `liters` decimal NOT NULL,
  `price_per_liter` decimal NOT NULL,
  `amount` decimal NOT NULL,
  `odometer_reading` decimal,
  `created_at` timestamp
);

CREATE TABLE `maintenance` (
  `id` int PRIMARY KEY,
  `vehicle_id` int NOT NULL,
  `maintenance_date` date NOT NULL,
  `odometer_reading` decimal,
  `maintenance_type` varchar(255) COMMENT 'preventive, corrective',
  `work_performed` varchar(255),
  `provider` varchar(255),
  `cost` decimal,
  `next_service_date` date,
  `next_service_km` decimal,
  `created_at` timestamp
);

CREATE TABLE `incidents` (
  `id` int PRIMARY KEY,
  `trip_id` int NOT NULL,
  `incident_date` date NOT NULL,
  `incident_time` time,
  `location` varchar(255),
  `incident_type` varchar(255) COMMENT 'accident, mechanical_failure, delay, road_closure, cargo_damage, documentation_issue',
  `description` varchar(255),
  `created_at` timestamp
);

CREATE TABLE `deliveries` (
  `id` int PRIMARY KEY,
  `trip_id` int UNIQUE NOT NULL,
  `actual_datetime` timestamp,
  `received_by` varchar(255),
  `evidence_reference` varchar(255),
  `status` varchar(255) NOT NULL,
  `created_at` timestamp
);

CREATE TABLE `invoices` (
  `id` int PRIMARY KEY,
  `client_id` int NOT NULL,
  `service_request_id` int NOT NULL,
  `invoice_number` varchar(255) UNIQUE NOT NULL,
  `amount` decimal NOT NULL,
  `issue_date` date NOT NULL,
  `due_date` date,
  `status` varchar(255) NOT NULL COMMENT 'pending, paid, overdue, cancelled',
  `created_at` timestamp
);

CREATE TABLE `payments` (
  `id` int PRIMARY KEY,
  `invoice_id` int NOT NULL,
  `amount` decimal NOT NULL,
  `payment_date` date NOT NULL,
  `payment_method` varchar(255),
  `created_at` timestamp
);

CREATE UNIQUE INDEX `role_permissions_index_0` ON `role_permissions` (`role_id`, `permission_id`);

ALTER TABLE `employees` ADD FOREIGN KEY (`id`) REFERENCES `users` (`employee_id`);

ALTER TABLE `users` ADD FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`);

ALTER TABLE `role_permissions` ADD FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`);

ALTER TABLE `role_permissions` ADD FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`);

ALTER TABLE `licenses` ADD FOREIGN KEY (`id`) REFERENCES `employees` (`license_id`);

ALTER TABLE `client_rates` ADD FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`);

ALTER TABLE `client_rates` ADD FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`);

ALTER TABLE `service_requests` ADD FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`);

ALTER TABLE `service_requests` ADD FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`);

ALTER TABLE `service_requests` ADD FOREIGN KEY (`created_by`) REFERENCES `users` (`id`);

ALTER TABLE `service_requests` ADD FOREIGN KEY (`id`) REFERENCES `trips` (`service_request_id`);

ALTER TABLE `trips` ADD FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`);

ALTER TABLE `trips` ADD FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`);

ALTER TABLE `trips` ADD FOREIGN KEY (`created_by`) REFERENCES `users` (`id`);

ALTER TABLE `expenses` ADD FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `advances` ADD FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `advances` ADD FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`);

ALTER TABLE `fuel_loads` ADD FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`);

ALTER TABLE `fuel_loads` ADD FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `maintenance` ADD FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`);

ALTER TABLE `incidents` ADD FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`);

ALTER TABLE `trips` ADD FOREIGN KEY (`id`) REFERENCES `deliveries` (`trip_id`);

ALTER TABLE `invoices` ADD FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`);

ALTER TABLE `invoices` ADD FOREIGN KEY (`service_request_id`) REFERENCES `service_requests` (`id`);

ALTER TABLE `payments` ADD FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`);
