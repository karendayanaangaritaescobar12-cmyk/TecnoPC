ALTER TABLE solicitudes ADD COLUMN contacto_json TEXT NULL;
ALTER TABLE compras ADD COLUMN contacto_json TEXT NULL;
ALTER TABLE compras ADD COLUMN preparacion VARCHAR(20) NOT NULL DEFAULT 'preparando';
ALTER TABLE pagos_online ADD COLUMN contacto_json TEXT NULL;

CREATE TABLE notificaciones (
 id VARCHAR(36) PRIMARY KEY,
 evento_clave VARCHAR(150) NOT NULL UNIQUE,
 canal VARCHAR(12) NOT NULL,
 destino VARCHAR(254) NOT NULL,
 evento VARCHAR(40) NOT NULL,
 referencia VARCHAR(64) NOT NULL,
 estado VARCHAR(16) NOT NULL DEFAULT 'pendiente',
 intentos INT NOT NULL DEFAULT 0,
 siguiente_intento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 bloqueo_hasta TIMESTAMP NULL,
 lease_token VARCHAR(36) NULL,
 proveedor_id VARCHAR(150) NULL,
 ultimo_error VARCHAR(80) NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 aceptado_en TIMESTAMP NULL
);
CREATE INDEX idx_notificaciones_pendientes ON notificaciones(estado,siguiente_intento);
