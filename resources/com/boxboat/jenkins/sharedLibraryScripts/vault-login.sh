#!/bin/sh

usage () {
    echo "Usage: VAULT_ROLE_ID=<vault-role-id> VAULT_SECRET_ID=<vault-secret-id> source vault-login.sh" >&2
}

help=0

while [ $# -gt 0 ]; do
    case "$1" in
        "-h" | "--help")
            help=1
            ;;
    esac
    shift
done

if [ $help -eq 1 ]; then
    usage
    return
fi

VAULT_AUTH_METHOD="ROLE"

if [ "${VAULT_ROLE_ID}" = "" -o "${VAULT_SECRET_ID}" = "" ]; then
  VAULT_AUTH_METHOD="TOKEN"
fi

if [ "${VAULT_AUTH_METHOD}" = "TOKEN" -a "${VAULT_TOKEN}" = "" ]; then
  echo "VAULT_TOKEN or VAULT_ROLE_ID and VAULT_SECRET_ID must be defined in the environment"
  return
fi

if [ "${VAULT_AUTH_METHOD}" = "ROLE" ]; then
export VAULT_TOKEN=$(vault write --format=json auth/approle/login \
    role_id=${VAULT_ROLE_ID} \
    secret_id=${VAULT_SECRET_ID} | jq -r .auth.client_token)
fi
