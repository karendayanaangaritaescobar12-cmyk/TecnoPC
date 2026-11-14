-- Additive migration: existing orders, sales and stock remain unchanged.
CREATE TABLE operaciones_idempotentes (
    ambito VARCHAR(32) NOT NULL,
    actor VARCHAR(80) NOT NULL,
    clave CHAR(36) NOT NULL,
    huella CHAR(64) NOT NULL,
    respuesta TEXT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ambito, actor, clave)
);

CREATE TABLE preferencias_checkout (
    referencia VARCHAR(64) NOT NULL PRIMARY KEY,
    estado VARCHAR(16) NOT NULL DEFAULT 'nueva',
    enlace VARCHAR(2048) NULL,
    iniciada_en TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (referencia) REFERENCES pagos_online(referencia)
);
