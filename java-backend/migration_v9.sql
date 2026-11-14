-- Ejecutar después de migration_v8.sql.
CREATE TABLE IF NOT EXISTS reparaciones (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 solicitud_id BIGINT UNSIGNED NOT NULL UNIQUE,
 equipo VARCHAR(120) NOT NULL,
 marca_modelo VARCHAR(120) NULL,
 serie VARCHAR(100) NULL,
 accesorios VARCHAR(250) NULL,
 diagnostico TEXT NULL,
 trabajo_realizado TEXT NULL,
 presupuesto DECIMAL(12,2) NULL,
 autorizado BOOLEAN NOT NULL DEFAULT FALSE,
 estado ENUM('recibido','diagnostico','esperando_autorizacion','en_reparacion','listo','entregado','cancelado') NOT NULL DEFAULT 'recibido',
 tecnico_usuario VARCHAR(60) NULL,
 fecha_entrega TIMESTAMP NULL,
 creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_rep_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes(id),
 INDEX idx_rep_estado (estado), INDEX idx_rep_tecnico (tecnico_usuario)
);
CREATE TABLE IF NOT EXISTS reparacion_eventos (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 reparacion_id BIGINT UNSIGNED NOT NULL,
 usuario VARCHAR(60) NOT NULL,
 evento VARCHAR(80) NOT NULL,
 detalle VARCHAR(500) NULL,
 creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (reparacion_id) REFERENCES reparaciones(id),
 INDEX idx_rep_eventos (reparacion_id,creado_en)
);
