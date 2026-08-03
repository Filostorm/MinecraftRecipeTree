CREATE TABLE IF NOT EXISTS `export_failure_reports` (
	`fingerprint` text PRIMARY KEY NOT NULL,
	`issue_number` integer,
	`issue_url` text,
	`status` text NOT NULL,
	`client_hash` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `export_failure_reports_rate_limit_idx` ON `export_failure_reports` (`client_hash`,`created_at`);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `export_failure_reports_created_at_idx` ON `export_failure_reports` (`created_at`);
