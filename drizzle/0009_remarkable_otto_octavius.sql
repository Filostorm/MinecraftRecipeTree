ALTER TABLE `users` ADD `avatar_url` text;--> statement-breakpoint
ALTER TABLE `users` ADD `avatar_key` text;--> statement-breakpoint
CREATE UNIQUE INDEX `users_avatar_key_idx` ON `users` (`avatar_key`);