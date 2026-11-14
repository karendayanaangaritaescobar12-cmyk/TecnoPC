-- Ejecutar después de migration_v9.sql.
ALTER TABLE reparaciones ADD COLUMN codigo_ingreso VARCHAR(24) NULL;
CREATE UNIQUE INDEX uq_reparacion_codigo_ingreso ON reparaciones(codigo_ingreso);
-- Las reparaciones anteriores pueden recibir códigos mediante un proceso administrativo seguro.
