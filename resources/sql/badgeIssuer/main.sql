--name: insert-selfie-badge<!
REPLACE INTO selfie_badge (id, creator_id, name, description, criteria, image, tags, issuable_from_gallery, deleted, ctime, mtime)
VALUES (:id, :creator_id, :name, :description, :criteria, :image, :tags, :issuable_from_gallery, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP())

--name: update-selfie-badge!
UPDATE selfie_badge SET name = :name, description = :description, criteria = :criteria, image = :image, tags = :tags, issuable_from_gallery= :issuable_from_gallery, mtime = UNIX_TIMESTAMP()
WHERE id = :id AND creator_id = :creator_id

--name: soft-delete-selfie-badge!
UPDATE selfie_badge SET deleted = 1, mtime = UNIX_TIMESTAMP() WHERE id = :id AND creator_id = :creator_id

--name: hard-delete-selfie-badge!
DELETE FROM selfie_badge WHERE id = :id AND creator_id = :creator_id

--name: get-user-selfie-badges
SELECT * FROM selfie_badge WHERE creator_id = :creator_id AND deleted = 0
GROUP BY mtime DESC

--name: get-selfie-badge
SELECT * FROM selfie_badge WHERE id = :id

--name: get-selfie-badge-creator
SELECT creator_id FROM selfie_badge WHERE id = :id

--name: get-assertion-json
SELECT assertion_json FROM user_badge WHERE id = :id AND deleted = 0

--name: select-badge-id-by-user-badge-id
SELECT badge_id FROM user_badge WHERE id = :user_badge_id

--name: select-badge-tags
SELECT tag FROM badge_content_tag WHERE badge_content_id = :id

--name: select-criteria-content-by-badge-id
SELECT cc.id, cc.language_code, cc.markdown_text, cc.url
FROM criteria_content AS cc
LEFT JOIN badge_criteria_content AS bcc ON cc.id = bcc.criteria_content_id
WHERE bcc.badge_id = :badge_id

--name: update-badge-criteria-url!
UPDATE criteria_content SET url = :url WHERE id = :id

--name: finalise-issued-user-badge!
UPDATE user_badge SET
issuer_id = :issuer_id,
assertion_url = :assertion_url,
assertion_json = :assertion_json,
selfie_id = :selfie_id
WHERE id = :id

--name: get-issuer-information
SELECT * FROM issuer_content WHERE id = :id

--name: get-criteria-page-information
SELECT bc.name, bc.image_file, bc.description, cc.id AS id, cc.markdown_text AS criteria_content
FROM badge AS badge
JOIN badge_badge_content AS bbc ON (bbc.badge_id = badge.id)
JOIN badge_content AS bc ON (bc.id = bbc.badge_content_id)
JOIN badge_criteria_content AS bcc ON (bcc.badge_id = badge.id)
JOIN criteria_content AS cc ON (cc.id = bcc.criteria_content_id)
WHERE cc.url = :url
--WHERE badge.id = (SELECT badge_id FROM badge_criteria_content WHERE criteria_content_id = :id LIMIT 1 )
--WHERE badge.id = :bid AND cc.url = :cid

--name: select-selfie-badge-issuing-history
SELECT ub.id, ub.user_id, ub.issued_on, ub.expires_on, ub.status, ub.revoked, u.first_name, u.last_name, u.profile_picture
FROM user_badge ub
JOIN user u ON (u.id=ub.user_id)
WHERE ub.selfie_id = :selfie_id AND ub.issuer_id = :issuer_id
ORDER BY ub.issued_on DESC
--GROUP BY ub.id

--name: select-multi-language-badge-content
--get badge by id
SELECT
badge.id as badge_id, badge.default_language_code,
bbc.badge_content_id,
bc.language_code,
bc.name, bc.description,
bc.image_file AS image,
ic.id AS issuer_content_id,
ic.url AS issuer_url,
cc.id AS criteria_content_id, cc.url AS criteria_url, cc.markdown_text AS criteria_content
FROM badge AS badge
JOIN badge_badge_content AS bbc ON (bbc.badge_id = badge.id)
JOIN badge_content AS bc ON (bc.id = bbc.badge_content_id)
JOIN badge_issuer_content AS bic ON (bic.badge_id = badge.id)
JOIN issuer_content AS ic ON (ic.id = bic.issuer_content_id)
JOIN badge_criteria_content AS bcc ON (bcc.badge_id = badge.id)
JOIN criteria_content AS cc ON (cc.id = bcc.criteria_content_id AND bc.language_code = cc.language_code AND ic.language_code = cc.language_code)
WHERE badge.id = :id
GROUP BY badge.id, bc.language_code, cc.language_code, ic.language_code, bbc.badge_content_id

--name: revoke-issued-selfie-badge!
UPDATE user_badge SET revoked = 1, mtime = UNIX_TIMESTAMP() WHERE id= :id AND issuer_id = :issuer_id

--name: select-selfie-issuer-by-badge-id
SELECT issuer_id FROM user_badge WHERE id=:id

--name: select-issued-badge-validity-status
SELECT id, revoked, deleted, assertion_url FROM user_badge WHERE id=:id

--name:check-badge-issuable
SELECT sb.issuable_from_gallery, ub.selfie_id FROM user_badge ub
JOIN selfie_badge sb ON sb.id = ub.selfie_id
WHERE ub.gallery_id = :id AND ub.deleted = 0
ORDER BY ub.ctime DESC

--name: delete-selfie-badges-all!
DELETE FROM selfie_badge WHERE creator_id = :user-id

--name: select-latest-selfie-badges-REMOVE
SELECT sb.id, sb.name, sb.image, g.id AS gallery_id FROM selfie_badge sb
LEFT JOIN user_badge ub ON ub.selfie_id = sb.id
LEFT JOIN gallery g ON g.id = ub.gallery_id
WHERE sb.issuable_from_gallery = 1 AND sb.deleted = 0 AND sb.creator_id != :user_id AND ub.user_id != :user_id AND ub.revoked = 1 AND ub.visibility !="private" AND ub.status = "accepted"
--WHERE sb.issuable_from_gallery = 1 AND sb.deleted = 0 AND sb.creator_id != :user_id AND (ub.id IS NULL OR ub.revoked = 1)
GROUP BY g.id
ORDER BY sb.mtime DESC LIMIT 5

--name: select-latest-selfie-badges
SELECT ub.gallery_id, g.badge_id, g.badge_name AS name, g.badge_image AS image_file, sb.id AS selfie_id, ub.user_id
FROM gallery g
INNER JOIN user_badge ub ON g.id = ub.gallery_id
INNER JOIN selfie_badge sb ON ub.selfie_id = sb.id
WHERE ub.status = 'accepted' AND ub.deleted = 0 AND ub.revoked = 0 AND (ub.expires_on IS NULL OR ub.expires_on > UNIX_TIMESTAMP())
      AND ub.visibility != 'private' AND ub.selfie_id IS NOT NULL AND sb.issuable_from_gallery = 1 AND sb.deleted = 0 AND sb.creator_id != :user_id
GROUP BY ub.gallery_id
ORDER BY sb.mtime DESC
LIMIT 1000

--name: select-user-gallery-ids
SELECT gallery_id, selfie_id FROM user_badge WHERE user_id = :user_id AND status != "declined" AND revoked = 0 AND selfie_id IS NOT NULL

--name: select-issuable-gallery-badges
SELECT ub.gallery_id, ub.selfie_id FROM user_badge ub
LEFT JOIN selfie_badge sb ON sb.id = ub.selfie_id
WHERE ub.gallery_id IN (:gallery_ids) AND selfie_id IS NOT NULL AND sb.issuable_from_gallery = 1
