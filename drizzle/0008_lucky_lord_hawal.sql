CREATE INDEX IF NOT EXISTS `account_recipe_favorites_user_leaderboard_idx` ON `account_recipe_favorites` (`pack_slug`,`publication_id`,`user_id`);--> statement-breakpoint
PRAGMA optimize;
