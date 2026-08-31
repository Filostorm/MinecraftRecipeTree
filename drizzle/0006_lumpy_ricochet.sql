CREATE TABLE IF NOT EXISTS `users` (
	`id` text PRIMARY KEY NOT NULL,
	`provider` text NOT NULL,
	`provider_user_id` text NOT NULL,
	`display_name` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS `users_provider_identity_idx` ON `users` (`provider`,`provider_user_id`);--> statement-breakpoint
CREATE TABLE IF NOT EXISTS `account_recipe_favorites` (
	`user_id` text NOT NULL,
	`pack_slug` text NOT NULL,
	`publication_id` text NOT NULL,
	`item_key` text NOT NULL,
	`recipe_category` integer NOT NULL,
	`recipe_index` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS `account_recipe_favorites_user_item_idx` ON `account_recipe_favorites` (`user_id`,`pack_slug`,`publication_id`,`item_key`);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `account_recipe_favorites_ranking_idx` ON `account_recipe_favorites` (`pack_slug`,`publication_id`,`item_key`,`recipe_category`,`recipe_index`);--> statement-breakpoint
PRAGMA optimize;
