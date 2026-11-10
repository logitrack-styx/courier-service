# Elegibilidad de repartidores en Sprint 4

El GET de disponibilidad sigue usando el estado del perfil. El comando `courier.assignment.requested.v1` añade un camino asíncrono que comprueba si el perfil existe y está ACTIVO. Courier publica elegibilidad. Shipment es dueño de la asignación definitiva.

La tabla `courier_assignment_eligibility` funciona como registro de comandos procesados y outbox de resultados. Un comando y una operación tienen restricciones únicas. El listener confirma después del commit. El worker reclama lotes de hasta 20, espera confirmación de enrutamiento sin transacción abierta y reutiliza el identificador al reintentar. Los comandos no procesables van a `courier.assignment.requests.v1.dlq`.

La primera migración Flyway crea únicamente esta tabla y sus índices. En esquemas Courier preexistentes, el baseline 0 registra el punto de partida sin cambiar las tablas de Sprint 3. La gestión anterior del esquema JPA del perfil sigue siendo deuda separada.

Activá la mensajería con `ASSIGNMENT_MESSAGING_ENABLED=true` y configura las variables `SPRING_RABBITMQ_*` del entorno. Los indicadores Micrometer registran resultados pendientes, publicaciones agotadas y profundidad de la DLQ.

La suite de regresión se ejecuta con Java 25 mediante `mvnw.cmd test`. Incluye reentrega de un comando con el mismo resultado persistido aunque cambie la disponibilidad del repartidor.
