#! /bin/sh

AUTH=`curl -q -s -i -H 'X-Storage-User: test:tester' -H 'X-Storage-Pass: testing' http://localhost:48080/auth/v1.0 |grep 'X-Auth-Token:' |awk -F ': ' '{ print $2 }'`
echo "You can authenticate on swift with the token $AUTH"
curl -I -H "X-Auth-Token: $AUTH" "http://localhost:48080/v1.0/AUTH_test/assets" -H "X-Container-Read: .r:*" -XPUT