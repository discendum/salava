CREATE TABLE `user_terms` (
  `user_id` bigint(20) NOT NULL,
  `status` enum('accepted','declined') DEFAULT 'declined',
  `ctime` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
