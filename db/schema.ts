import {sql} from 'drizzle-orm';
import {index, integer, sqliteTable, text, uniqueIndex} from 'drizzle-orm/sqlite-core';

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

export const feedbackReports = sqliteTable(
  'feedback_reports',
  {
    id: text('id').primaryKey(),
    kind: text('kind', {enum: ['bug', 'feature']}).notNull(),
    title: text('title').notNull().default(''),
    message: text('message').notNull(),
    contact: text('contact'),
    packSlug: text('pack_slug'),
    packName: text('pack_name'),
    pageUrl: text('page_url'),
    userAgent: text('user_agent'),
    fingerprintHash: text('fingerprint_hash').notNull(),
    createdAt: integer('created_at').notNull(),
  },
  table => [
    index('feedback_reports_rate_limit_idx').on(table.fingerprintHash, table.createdAt),
    index('feedback_reports_created_at_idx').on(table.createdAt),
  ],
);
