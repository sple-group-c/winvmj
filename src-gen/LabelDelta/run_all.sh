#!/bin/bash

cleanup() {
    echo "Exiting script..."
    pkill -P $$
    exit 1
}

trap cleanup SIGINT

read -p "Enter the path to the frontend directory: " frontend_dir

echo "SELECT 'CREATE DATABASE schedulingmanagementsystem_product_labeldelta' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'schedulingmanagementsystem_product_labeldelta') \gexec" | psql "postgresql://postgres:1234@localhost"
for file in sql/*.sql; do
    psql -a -f "$file" "postgresql://postgres:1234@localhost/schedulingmanagementsystem_product_labeldelta"
done

java -cp schedulingmanagementsystem.product.labeldelta --module-path schedulingmanagementsystem.product.labeldelta -m schedulingmanagementsystem.product.labeldelta &

cd $frontend_dir && {
    npm install && {
        npm run json:server &
        npm run start &
    }
}

wait