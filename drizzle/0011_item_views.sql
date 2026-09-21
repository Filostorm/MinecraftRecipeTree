CREATE TABLE `item_view_totals` (
	`pack_slug` text NOT NULL,
	`item_key` text NOT NULL,
	`views` integer DEFAULT 0 NOT NULL,
	PRIMARY KEY(`pack_slug`, `item_key`)
);
--> statement-breakpoint
CREATE INDEX `item_view_ranking` ON `item_view_totals` (`pack_slug`,"views" DESC,`item_key`);