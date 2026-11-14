# Ventanas del límite de solicitudes públicas

`PublicRequestFilter` conserva como máximo 8192 ventanas por proceso. Cada
ventana corresponde a una IP y una ruta normalizada; las dos rutas de pagos
siguen compartiendo el presupuesto de diez solicitudes por minuto.

## Cambio de comportamiento

- Durante el tráfico, se eliminan ventanas vencidas cada diez segundos. No
  se crea un hilo adicional. Sin solicitudes no se ejecuta la limpieza;
  la memoria sigue limitada y se limpia al volver a recibir tráfico.
- Al llenarse el mapa, primero se eliminan todas las ventanas vencidas.
- Si las 8192 ventanas siguen activas, se retira la menos recientemente
  utilizada y se admite al visitante nuevo. Llenar el mapa ya no provoca
  un rechazo general para clientes nuevos.
- El reloj usa tiempo monotónico para que un ajuste de la hora del equipo
  no alargue ni acorte las ventanas. Las ventanas duran un minuto desde
  su primera solicitud; los rechazos no prolongan ese plazo.
- La decisión y actualización siguen sincronizadas: solicitudes simultáneas
  de la misma IP no pueden superar la cuota de una ventana conservada.

No se agregan dependencias: se usa `LinkedHashMap` de Java con orden de acceso.
El máximo de memoria y la admisión de nuevos clientes implican una concesión:
cuando una ventana activa es retirada, su contador se pierde. Un visitante
cuya ventana fue retirada puede comenzar otra antes de cumplirse el minuto.
Un ataque con muchas IP puede debilitar el límite mediante esa rotación.
Una caché con expulsión por tamaño, como Caffeine, también necesita considerar
este efecto. Para cuotas estrictas a mayor escala se necesita capacidad
adicional o un mecanismo de límites en la infraestructura.

## Verificación

Desde la raíz del proyecto, usando Java 21 y Maven:

```powershell
mvn -f java-backend/pom.xml "-Dtest=PublicRequestFilterTest,TrustedProxyHttpTest,SecurityConfigTest,PublicApiTest" test
```

`PublicRequestFilterTest` verifica saturación con más de 8192 clientes,
expiración sin esperas reales, eliminación de ventanas vencidas y 64
solicitudes concurrentes con exactamente diez admisiones. También conserva
las pruebas de rutas equivalentes y rutas desconocidas.

`TrustedProxyHttpTest` comprueba el aislamiento por IP con Tomcat real.
`SecurityConfigTest` y `PublicApiTest` cubren las reglas de acceso y los
comportamientos existentes de la API. Las pruebas no crean pagos reales.
