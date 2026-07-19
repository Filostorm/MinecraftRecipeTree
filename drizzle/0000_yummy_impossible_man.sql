CREATE TABLE IF NOT EXISTS `modpacks` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`minecraft_version` text NOT NULL,
	`snapshot_json` text NOT NULL,
	`revision` integer DEFAULT 1 NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
