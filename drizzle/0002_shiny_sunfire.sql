CREATE TABLE IF NOT EXISTS `feedback_reports` (
	`id` text PRIMARY KEY NOT NULL,
	`kind` text NOT NULL,
	`message` text NOT NULL,
	`contact` text,
	`pack_slug` text,
	`pack_name` text,
	`page_url` text,
	`user_agent` text,
	`fingerprint_hash` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `feedback_reports_rate_limit_idx` ON `feedback_reports` (`fingerprint_hash`,`created_at`);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `feedback_reports_created_at_idx` ON `feedback_reports` (`created_at`);
