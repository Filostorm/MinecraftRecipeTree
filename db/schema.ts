import {sql} from 'drizzle-orm';
import {integer, sqliteTable, text, uniqueIndex} from 'drizzle-orm/sqlite-core';

export const modpacks = sqliteTable('modpacks', {
  id: text('id').primaryKey(),
  name: text('name').notNull(),
  minecraftVersion: text('minecraft_version').notNull(),
  snapshotJson: text('snapshot_json').notNull(),
  revision: integer('revision').notNull().default(1),
  createdAt: integer('created_at').notNull(),
  updatedAt: integer('updated_at').notNull(),
});

export const datasetPublications = sqliteTable('dataset_publications', {
  publicationId: text('publication_id').primaryKey(),
  manifestSha256: text('manifest_sha256').notNull(),
  objectCount: integer('object_count').notNull(),
  storedBytes: integer('stored_bytes').notNull(),
  committedAt: integer('committed_at').notNull(),
});

export const datasetChannels = sqliteTable(
  'dataset_channels',
  {
    slug: text('slug').primaryKey(),
    displayName: text('display_name').notNull(),
    minecraftVersion: text('minecraft_version').notNull(),
    packVersion: text('pack_version').notNull(),
    publicationId: text('publication_id')
      .notNull()
      .references(() => datasetPublications.publicationId),
    previewAssetSetId: text('preview_asset_set_id').notNull(),
    isDefault: integer('is_default').notNull().default(0),
    revision: integer('revision').notNull().default(1),
    activatedAt: integer('activated_at').notNull(),
  },
  table => [
    uniqueIndex('dataset_channels_one_default_idx')
      .on(table.isDefault)
      .where(sql`${table.isDefault} = 1`),
    uniqueIndex('dataset_channels_publication_idx').on(table.publicationId),
    uniqueIndex('dataset_channels_preview_asset_set_idx').on(table.previewAssetSetId),
  ],
);
