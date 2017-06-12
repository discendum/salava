
-- name: select-user-pages
-- get user's pages
SELECT p.id, name, description, theme, border, padding, visibility, password, visible_after, visible_before, ctime, mtime, GROUP_CONCAT(DISTINCT pb.badge_id) AS badges, GROUP_CONCAT(DISTINCT pt.tag) AS tags FROM page AS p
       LEFT JOIN page_block_badge AS pb ON pb.page_id = p.id
       LEFT JOIN page_tag AS pt ON pt.page_id = p.id
       WHERE user_id = :user_id AND p.deleted = 0
       GROUP BY p.id, name, description, theme, border, padding, visibility, password, visible_after, visible_before, ctime, mtime

-- name: insert-empty-page<!
-- create a new empty page
INSERT INTO page (user_id, name, visibility, ctime, mtime) VALUES (:user_id, :name, 'private', UNIX_TIMESTAMP(), UNIX_TIMESTAMP())

-- name: select-page
-- get page
SELECT p.id, name, description, theme, border, padding, visibility, password, visible_after, visible_before, p.ctime, p.mtime, user_id, u.first_name, u.last_name, GROUP_CONCAT(pt.tag) AS tags FROM page AS p
       JOIN user AS u ON u.id = p.user_id
       LEFT JOIN page_tag AS pt ON pt.page_id = p.id
       WHERE p.id = 1 AND p.deleted = 0
       GROUP BY p.id, name, description, theme, border, padding, visibility, password, visible_after, visible_before, p.ctime, p.mtime, user_id, u.first_name, u.last_name

-- name: select-pages-badge-blocks
SELECT pb.id, 'badge' AS type, block_order, pb.badge_id, format, ub.issued_on, bc.name, bc.description, bc.image_file, cc.url AS criteria_url, ube.url AS evidence_url, ub.show_evidence, cc.markdown_text AS criteria_content, ic.name AS issuer_content_name, ic.url AS issuer_content_url, ic.email AS issuer_email, ic.image_file AS issuer_image, crc.name AS creator_name, crc.url AS creator_url, crc.email AS creator_email, crc.image_file AS creator_image FROM page_block_badge AS pb
       JOIN user_badge AS ub ON pb.badge_id = ub.id
       JOIN badge AS badge ON (badge.id = ub.badge_id)
       JOIN badge_content AS bc ON (bc.id = ub.badge_id) AND bc.language_code = badge.default_language_code
       JOIN issuer_content AS ic ON (ic.id = ub.badge_id) AND ic.language_code = badge.default_language_code
       JOIN criteria_content AS cc ON (cc.id = ub.badge_id) AND bc.language_code = badge.default_language_code
       JOIN user_badge_evidence AS ube ON (ube.id = ub.badge_id)
       JOIN badge_creator_content AS bcrc ON (bcrc.badge_id = ub.badge_id)
       JOIN creator_content AS crc ON (crc.id = bcrc.creator_content_id)  AND crc.language_code = badge.default_language_code
       WHERE page_id = :page_id


-- name: select-pages-files-blocks
SELECT id, 'file' AS type, block_order FROM page_block_files
       WHERE page_id = :page_id

-- name: select-pages-heading-blocks
SELECT id, 'heading' AS type, block_order, size, content  FROM page_block_heading
       WHERE page_id = :page_id

-- name: select-pages-html-blocks
SELECT id, 'html' AS type, block_order, content FROM page_block_html
       WHERE page_id = :page_id

-- name: select-pages-tag-blocks
SELECT id, 'tag' AS type, block_order, tag, format, sort FROM page_block_tag
       WHERE page_id = :page_id

--name: select-page-owner
--fetch page's owner
SELECT user_id FROM page WHERE id = :id

--name: select-files-block-content
--get files in file block
SELECT f.id, f.name, f.path, f.size, f.mime_type, pb.file_order FROM user_file AS f
       JOIN page_block_files_has_file AS pb ON pb.file_id = f.id
       WHERE pb.block_id = :block_id
       ORDER BY pb.file_order

--name: update-page-name-description!
--update name and description of the page
UPDATE page SET name = :name, description = :description WHERE id = :id

--name: delete-heading-blocks!
DELETE FROM page_block_heading WHERE page_id = :page_id

--name: delete-badge-blocks!
DELETE FROM page_block_badge WHERE page_id = :page_id

--name: delete-html-blocks!
DELETE FROM page_block_html WHERE page_id = :page_id

--name: delete-files-blocks!
DELETE FROM page_block_files WHERE page_id = :page_id

--name: delete-tag-blocks!
DELETE FROM page_block_tag WHERE page_id = :page_id

--name: delete-heading-block!
DELETE FROM page_block_heading WHERE id = :id

--name: delete-badge-block!
DELETE FROM page_block_badge WHERE id = :id

--name: delete-html-block!
DELETE FROM page_block_html WHERE id = :id

--name: delete-files-block!
DELETE FROM page_block_files WHERE id = :id

--name: delete-tag-block!
DELETE FROM page_block_tag WHERE id = :id

--name: delete-files-block-files!
DELETE FROM page_block_files_has_file WHERE block_id = :block_id

--name: update-heading-block!
UPDATE page_block_heading SET size = :size, content = :content, block_order = :block_order WHERE id = :id AND page_id = :page_id

--name: insert-heading-block!
INSERT INTO page_block_heading (page_id, size, content, block_order) values (:page_id, :size, :content, :block_order)

--name: update-badge-block!
UPDATE page_block_badge SET badge_id = :badge_id, format = :format, block_order = :block_order WHERE id = :id AND page_id = :page_id

--name: insert-badge-block!
INSERT INTO page_block_badge (page_id, badge_id, format, block_order) values (:page_id, :badge_id, :format, :block_order)

--name: update-html-block!
UPDATE page_block_html SET content = :content, block_order = :block_order WHERE id = :id AND page_id = :page_id

--name: insert-html-block!
INSERT INTO page_block_html (page_id, content, block_order) values (:page_id, :content, :block_order)

--name: update-files-block!
UPDATE page_block_files SET block_order = :block_order WHERE id = :id AND page_id = :page_id

--name: insert-files-block<!
INSERT INTO page_block_files (page_id, block_order) VALUES (:page_id, :block_order)

--name: insert-files-block-file!
INSERT INTO page_block_files_has_file (block_id, file_id, file_order) VALUES (:block_id, :file_id, :file_order)

--name: update-tag-block!
UPDATE page_block_tag SET tag = :tag, format = :format, sort = :sort, block_order = :block_order WHERE id = :id AND page_id = :page_id

--name: insert-tag-block!
INSERT INTO page_block_tag (page_id, tag, format, sort, block_order) VALUES (:page_id, :tag, :format, :sort, :block_order)

--name: update-page-theme!
UPDATE page SET theme = :theme, border = :border, padding = :padding WHERE id = :id

--name: update-page-visibility-and-password!
UPDATE page SET visibility = :visibility, password = :password WHERE id = :id

--name: replace-page-tag!
REPLACE INTO page_tag (page_id, tag)
       VALUES (:page_id, :tag)

--name: delete-page-tags!
DELETE FROM page_tag WHERE page_id = :page_id

--name: delete-page!
DELETE FROM page WHERE id = :id

--name: select-page-owner
--get page owner's user-id
SELECT user_id FROM page WHERE id = :id

--name: update-page-visibility!
UPDATE page SET visibility = :visibility, mtime = UNIX_TIMESTAMP() WHERE id = :id

--name: select-user-language
SELECT language FROM user WHERE id = :id
