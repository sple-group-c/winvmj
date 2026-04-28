#!/bin/bash

cleanup() {
    echo "Exiting script..."
    pkill -P $$
    exit 1
}

trap cleanup SIGINT

read -p "Enter the path to the frontend directory: " frontend_dir

echo "SELECT 'CREATE DATABASE schedulingmanagementsystem_product_announcementsystem' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'schedulingmanagementsystem_product_announcementsystem') \gexec" | psql "postgresql://:@localhost"
for file in sql/*.sql; do
    psql -a -f "$file" "postgresql://:@localhost/schedulingmanagementsystem_product_announcementsystem"
done

java -cp schedulingmanagementsystem.product.announcementsystem --module-path schedulingmanagementsystem.product.announcementsystem -m schedulingmanagementsystem.product.announcementsystem &

cd $frontend_dir && {
    npm install && {
        npm run json:server &
        npm run start &
    }
}

wait