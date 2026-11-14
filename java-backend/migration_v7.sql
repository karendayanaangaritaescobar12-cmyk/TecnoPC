-- Ejecutar una sola vez sobre la base de datos existente.
USE tecnopc;
ALTER TABLE compras ADD COLUMN vendedor_usuario VARCHAR(80) NULL;
CREATE INDEX idx_compras_vendedor ON compras(vendedor_usuario);
