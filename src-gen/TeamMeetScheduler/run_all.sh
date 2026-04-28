#!/bin/bash

cleanup() {
    echo "Exiting script..."
    pkill -P $$
    exit 1
}

trap cleanup SIGINT

read -p "Enter the path to the frontend directory: " frontend_dir

echo "SELECT 'CREATE DATABASE schedulingmanagementsystem_product_teammeetscheduler' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'schedulingmanagementsystem_product_teammeetscheduler') \gexec" | psql "postgresql://:@localhost"
for file in sql/*.sql; do
    psql -a -f "$file" "postgresql://:@localhost/schedulingmanagementsystem_product_teammeetscheduler"
done

java -cp schedulingmanagementsystem.product.teammeetscheduler --module-path schedulingmanagementsystem.product.teammeetscheduler -m schedulingmanagementsystem.product.teammeetscheduler &

cd $frontend_dir && {
    npm install && {
        npm run json:server &
        npm run start &
    }
}

wait