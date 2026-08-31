CREATE TABLE IF NOT EXISTS `donation_contributions` (
	`contribution_id` text PRIMARY KEY NOT NULL,
	`donor_key` text NOT NULL,
	`public_name` text,
	`cadence` text NOT NULL,
	`stripe_payment_intent_id` text,
	`gross_cents` integer NOT NULL,
	`currency` text NOT NULL,
	`paid_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `donation_contributions_week_idx` ON `donation_contributions` (`currency`,`paid_at`);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `donation_contributions_donor_idx` ON `donation_contributions` (`donor_key`,`paid_at`);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `donation_contributions_payment_intent_idx` ON `donation_contributions` (`stripe_payment_intent_id`);--> statement-breakpoint
CREATE TABLE IF NOT EXISTS `donation_refunds` (
	`stripe_charge_id` text PRIMARY KEY NOT NULL,
	`stripe_payment_intent_id` text NOT NULL,
	`refunded_cents` integer NOT NULL,
	`currency` text NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `donation_refunds_payment_intent_idx` ON `donation_refunds` (`stripe_payment_intent_id`);--> statement-breakpoint
CREATE TABLE IF NOT EXISTS `donation_webhook_events` (
	`event_id` text PRIMARY KEY NOT NULL,
	`event_type` text NOT NULL,
	`processed_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `donation_webhook_events_processed_idx` ON `donation_webhook_events` (`processed_at`);
