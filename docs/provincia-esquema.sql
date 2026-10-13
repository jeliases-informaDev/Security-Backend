-- MySQL 8. Ejecutar en la misma base de datos del backend.
USE security_db;
CREATE TABLE IF NOT EXISTS provincia (
    id INT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    id_departamento INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_provincia_departamento_nombre UNIQUE (id_departamento, nombre),
    CONSTRAINT fk_provincia_departamento FOREIGN KEY (id_departamento)
        REFERENCES scoring_departamento(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
