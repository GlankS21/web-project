#!/bin/bash
# Creates one database and one dedicated login role per microservice (database-per-service).
# Each role owns only its own database and has no access to the others.
# Runs once, when the PostgreSQL data volume is initialised for the first time.
set -euo pipefail

create_service_database() {
    local database="$1"
    local role="$2"
    local password="$3"

    if [ -z "$password" ]; then
        echo "Password for role '$role' is empty, refusing to continue" >&2
        exit 1
    fi

    echo "Creating database '$database' owned by role '$role'"
    psql -v ON_ERROR_STOP=1 \
         --username "$POSTGRES_USER" \
         --dbname postgres \
         -v db="$database" \
         -v role="$role" \
         -v password="$password" <<'EOSQL'
CREATE ROLE :"role" WITH LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"role";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE :"db" TO :"role";
EOSQL
}

create_service_database auth_db         auth_user         "${AUTH_DB_PASSWORD:-}"
create_service_database user_db         user_user         "${USER_DB_PASSWORD:-}"
create_service_database product_db      product_user      "${PRODUCT_DB_PASSWORD:-}"
create_service_database inventory_db    inventory_user    "${INVENTORY_DB_PASSWORD:-}"
create_service_database cart_db         cart_user         "${CART_DB_PASSWORD:-}"
create_service_database order_db        order_user        "${ORDER_DB_PASSWORD:-}"
create_service_database payment_db      payment_user      "${PAYMENT_DB_PASSWORD:-}"
create_service_database notification_db notification_user "${NOTIFICATION_DB_PASSWORD:-}"
