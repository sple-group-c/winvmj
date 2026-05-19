#!/bin/bash

cleanup() {
    echo "Exiting script..."
    pkill -P $$
    exit 1
}

trap cleanup SIGINT

read -p "Enter the path to the frontend directory: " frontend_dir

echo "SELECT 'CREATE DATABASE spleproyekassoc_product_zaruman' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'spleproyekassoc_product_zaruman') \gexec" | psql "postgresql://:@localhost"
for file in sql/*.sql; do
    psql -a -f "$file" "postgresql://:@localhost/spleproyekassoc_product_zaruman"
done

java -cp spleproyekassoc.product.zaruman --module-path spleproyekassoc.product.zaruman -m spleproyekassoc.product.zaruman &

cd $frontend_dir && {
    npm install && {
        npm run json:server &
        npm run start &
    }
}

wait