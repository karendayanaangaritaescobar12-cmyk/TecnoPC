-- Ejecutar una sola vez después de database.sql. MySQL 8+.
CREATE TABLE pagos_online (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 referencia VARCHAR(64) NOT NULL UNIQUE,
 proveedor VARCHAR(30) NOT NULL,
 estado ENUM('pendiente','aprobado','rechazado','cancelado','revision') NOT NULL DEFAULT 'pendiente',
 cliente_nombre VARCHAR(80) NOT NULL,
 total_cop DECIMAL(12,2) NOT NULL,
 proveedor_pago_id VARCHAR(100) NULL UNIQUE,
 compra_id BIGINT UNSIGNED NULL UNIQUE,
 creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (compra_id) REFERENCES compras(id)
);
CREATE TABLE pago_items (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 pago_id BIGINT UNSIGNED NOT NULL,
 producto_id INT UNSIGNED NOT NULL,
 nombre VARCHAR(80) NOT NULL,
 cantidad INT UNSIGNED NOT NULL,
 precio DECIMAL(12,2) NOT NULL,
 FOREIGN KEY (pago_id) REFERENCES pagos_online(id),
 FOREIGN KEY (producto_id) REFERENCES productos(id)
);
