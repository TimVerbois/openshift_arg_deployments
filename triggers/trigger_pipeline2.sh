#!/bin/ksh

curl -H "Content-Type: application/yaml" --data-binary @payload_file.yaml -X POST -k https://openshift.cegeka.com:443/apis/build.openshift.io/v1/namespaces/ci00000000-argentatest-01/buildconfigs/helloworld2-pl/webhooks/ZThlOTlmMzU0YjE2NTQwYg/generic
