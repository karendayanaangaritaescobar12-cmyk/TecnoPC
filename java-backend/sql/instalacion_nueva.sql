-- Solo para base nueva y vacia. No ejecutar sobre datos existentes.

-- java-backend\sql\database.sql
CREATE DATABASE IF NOT EXISTS tecnopc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tecnopc;

CREATE TABLE productos (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(80) NOT NULL,
  categoria ENUM('pc','laptop','cpu','gpu','ram','ssd','mb','psu','case') NOT NULL,
  sku VARCHAR(30) UNIQUE,
  especificaciones VARCHAR(160),
  stock INT UNSIGNED NOT NULL DEFAULT 0,
  stock_minimo INT UNSIGNED NOT NULL DEFAULT 2,
  costo DECIMAL(12,2) NOT NULL DEFAULT 0,
  precio DECIMAL(12,2) NOT NULL DEFAULT 0,
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_productos_categoria_stock (categoria, stock)
);

CREATE TABLE solicitudes (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tipo ENUM('servicio','cotizacion') NOT NULL,
  nombre VARCHAR(80) NOT NULL,
  telefono VARCHAR(30) NOT NULL,
  detalle VARCHAR(600) NOT NULL,
  estado ENUM('nuevo','en proceso','listo','cerrado') NOT NULL DEFAULT 'nuevo',
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_solicitudes_estado_fecha (estado, creado_en)
);

CREATE TABLE facturas (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  numero VARCHAR(20) NOT NULL UNIQUE,
  cliente VARCHAR(80) NOT NULL,
  documento VARCHAR(20),
  subtotal DECIMAL(12,2) NOT NULL,
  descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
  iva DECIMAL(12,2) NOT NULL,
  total DECIMAL(12,2) NOT NULL,
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE factura_detalles (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  factura_id BIGINT UNSIGNED NOT NULL,
  producto_id INT UNSIGNED NULL,
  nombre_producto VARCHAR(80) NOT NULL,
  cantidad INT UNSIGNED NOT NULL,
  precio_unitario DECIMAL(12,2) NOT NULL,
  FOREIGN KEY (factura_id) REFERENCES facturas(id),
  FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE SET NULL
);

CREATE TABLE auditoria (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  usuario VARCHAR(80) NOT NULL DEFAULT 'sistema',
  accion VARCHAR(80) NOT NULL,
  detalle VARCHAR(250),
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- Compras: cada compra queda registrada con sus totales y número único.
CREATE TABLE compras (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  numero VARCHAR(24) NOT NULL UNIQUE,
  cliente_nombre VARCHAR(80) NOT NULL,
  cliente_documento VARCHAR(30) NULL,
  subtotal DECIMAL(12,2) NOT NULL,
  iva DECIMAL(12,2) NOT NULL,
  total DECIMAL(12,2) NOT NULL,
  estado ENUM('registrada','cancelada') NOT NULL DEFAULT 'registrada',
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_compras_fecha (creado_en)
);

CREATE TABLE compra_detalles (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  compra_id BIGINT UNSIGNED NOT NULL,
  producto_id INT UNSIGNED NOT NULL,
  nombre_producto VARCHAR(80) NOT NULL,
  cantidad INT UNSIGNED NOT NULL,
  precio_unitario DECIMAL(12,2) NOT NULL,
  subtotal DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_detalle_compra FOREIGN KEY (compra_id) REFERENCES compras(id),
  CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

-- Empleados: datos básicos para el módulo administrativo.
CREATE TABLE empleados (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombres VARCHAR(80) NOT NULL,
  apellidos VARCHAR(80) NOT NULL,
  documento VARCHAR(30) NOT NULL UNIQUE,
  cargo VARCHAR(60) NOT NULL,
  telefono VARCHAR(30) NULL,
  correo VARCHAR(120) NULL,
  estado ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);


-- Historial de conversaciones del asistente de TecnoPC.
CREATE TABLE chat_mensajes (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sesion VARCHAR(64) NOT NULL,
  remitente ENUM('cliente','bot') NOT NULL,
  mensaje TEXT NOT NULL,
  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_chat_sesion_fecha (sesion, creado_en)
);


-- java-backend\migration_v7.sql
-- Ejecutar una sola vez sobre la base de datos existente.
USE tecnopc;
ALTER TABLE compras ADD COLUMN vendedor_usuario VARCHAR(80) NULL;
CREATE INDEX idx_compras_vendedor ON compras(vendedor_usuario);


-- java-backend\migration_v8.sql
-- Ejecutar después de database.sql y migration_v7.sql.
CREATE TABLE IF NOT EXISTS usuarios (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 empleado_id INT UNSIGNED NULL UNIQUE,
 usuario VARCHAR(60) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 rol ENUM('ADMIN','VENDEDOR','TECNICO') NOT NULL,
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (empleado_id) REFERENCES empleados(id) ON DELETE SET NULL
);


-- java-backend\migration_v9.sql
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


-- java-backend\migration_v10.sql
-- Ejecutar después de migration_v9.sql.
ALTER TABLE reparaciones ADD COLUMN codigo_ingreso VARCHAR(24) NULL;
CREATE UNIQUE INDEX uq_reparacion_codigo_ingreso ON reparaciones(codigo_ingreso);
-- Las reparaciones anteriores pueden recibir códigos mediante un proceso administrativo seguro.


-- java-backend\sql\migration_v12.sql
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

