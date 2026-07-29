CREATE TABLE IF NOT EXISTS `dataset_channels` (
	`slug` text PRIMARY KEY NOT NULL,
	`display_name` text NOT NULL,
	`minecraft_version` text NOT NULL,
	`pack_version` text NOT NULL,
	`publication_id` text NOT NULL,
	`preview_asset_set_id` text NOT NULL,
	`is_default` integer DEFAULT 0 NOT NULL,
	`revision` integer DEFAULT 1 NOT NULL,
	`activated_at` integer NOT NULL,
	FOREIGN KEY (`publication_id`) REFERENCES `dataset_publications`(`publication_id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS `dataset_channels_one_default_idx` ON `dataset_channels` (`is_default`) WHERE "dataset_channels"."is_default" = 1;--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS `dataset_channels_publication_idx` ON `dataset_channels` (`publication_id`);--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS `dataset_channels_preview_asset_set_idx` ON `dataset_channels` (`preview_asset_set_id`);--> statement-breakpoint
CREATE TABLE IF NOT EXISTS `dataset_publications` (
	`publication_id` text PRIMARY KEY NOT NULL,
	`manifest_sha256` text NOT NULL,
	`object_count` integer NOT NULL,
	`stored_bytes` integer NOT NULL,
	`committed_at` integer NOT NULL
);
