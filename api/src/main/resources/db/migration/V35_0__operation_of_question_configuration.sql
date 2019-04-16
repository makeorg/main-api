ALTER TABLE operation_of_question
  ADD COLUMN intro_card_enabled BOOLEAN DEFAULT false,
  ADD COLUMN intro_card_title STRING(256),
  ADD COLUMN intro_card_description STRING(1024),
  ADD COLUMN push_proposal_card_enabled BOOLEAN DEFAULT false,
  ADD COLUMN signup_card_enabled BOOLEAN DEFAULT false,
  ADD COLUMN signup_card_title STRING(256),
  ADD COLUMN signup_card_next_cta STRING(256),
  ADD COLUMN final_card_enabled BOOLEAN DEFAULT false,
  ADD COLUMN final_card_sharing_enabled BOOLEAN DEFAULT false,
  ADD COLUMN final_card_title STRING(256),
  ADD COLUMN final_card_share_description STRING(1024),
  ADD COLUMN final_card_learn_more_title STRING(256),
  ADD COLUMN final_card_learn_more_button STRING(256),
  ADD COLUMN final_card_link_url STRING(2048),
  ADD COLUMN about_url STRING(2048),
  ADD COLUMN meta_title STRING(256),
  ADD COLUMN meta_description STRING(1024),
  ADD COLUMN meta_picture STRING(1024);

COMMIT;
