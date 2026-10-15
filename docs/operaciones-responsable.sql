-- MySQL 8: ejecutar sobre la base de datos del backend antes de reiniciarlo.
-- Compatible con una columna ya creada por Hibernate ddl-auto=update.
SET @ddl_responsable = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'registro_operaciones_v2' AND column_name = 'id_usuario_responsable'),
    'SELECT 1',
    'ALTER TABLE registro_operaciones_v2 ADD COLUMN id_usuario_responsable INT NULL'
);
PREPARE stmt_responsable FROM @ddl_responsable;
EXECUTE stmt_responsable;
DEALLOCATE PREPARE stmt_responsable;

SET @ddl_responsable = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
        AND table_name = 'registro_operaciones_v2' AND constraint_name = 'fk_reg_v2_responsable'),
    'SELECT 1',
    'ALTER TABLE registro_operaciones_v2 ADD CONSTRAINT fk_reg_v2_responsable FOREIGN KEY (id_usuario_responsable) REFERENCES usuarios(id)'
);
PREPARE stmt_responsable FROM @ddl_responsable;
EXECUTE stmt_responsable;
DEALLOCATE PREPARE stmt_responsable;
