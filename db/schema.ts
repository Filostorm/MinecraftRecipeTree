import {integer, sqliteTable, text} from 'drizzle-orm/sqlite-core';

export const modpacks = sqliteTable('modpacks', {
  id: text('id').primaryKey(),
  name: text('name').notNull(),
  minecraftVersion: text('minecraft_version').notNull(),
  snapshotJson: text('snapshot_json').notNull(),
  revision: integer('revision').notNull().default(1),
  createdAt: integer('created_at').notNull(),
  updatedAt: integer('updated_at').notNull(),
});
