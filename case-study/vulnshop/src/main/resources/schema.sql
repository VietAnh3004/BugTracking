DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS products;

CREATE TABLE users (
    id            BIGINT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL
);

CREATE TABLE products (
    id       BIGINT PRIMARY KEY,
    name     VARCHAR(255)   NOT NULL,
    price    DECIMAL(12, 2) NOT NULL,
    category VARCHAR(100)   NOT NULL
);
