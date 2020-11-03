UPDATE oauth_client SET allowed_grant_types = 'password,refresh_token' where allowed_grant_types = '{"password", "refresh_token", "client_credentials"}';

commit;