CREATE TABLE IF NOT EXISTS `recipe_favorites` (
	`pack_slug` text NOT NULL,
	`publication_id` text NOT NULL,
	`item_key` text NOT NULL,
	`client_hash` text NOT NULL,
	`recipe_category` integer NOT NULL,
	`recipe_index` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS `recipe_favorites_user_item_idx` ON `recipe_favorites` (`pack_slug`,`publication_id`,`item_key`,`client_hash`);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS `recipe_favorites_ranking_idx` ON `recipe_favorites` (`pack_slug`,`publication_id`,`item_key`,`recipe_category`,`recipe_index`);
