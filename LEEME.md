# Canchas Deportivas — Firebase Spark

Esta copia reemplaza la dependencia de Cloud Functions por transacciones y reglas de Firestore. No necesita habilitar facturación. La carpeta original y la entrega anterior se conservan.

**Todavía no hay un proyecto Firebase real conectado.** El APK incluido es de prueba local: necesita los emuladores de Firebase en la computadora. Para usarlo sin esa computadora, crea tu proyecto Spark y recompila con su configuración siguiendo `GUIA_FIREBASE.md`.

## Los cuatro requisitos del curso

| Requisito | Implementación | Dónde demostrarlo |
| --- | --- | --- |
| Persistencia local | SQLiteOpenHelper, archivo privado `canchas_cache.db`, caché por UID de perfil, catálogo y reservas propias. Se borra al salir. | Carga datos como cliente; sin cerrar sesión desactiva la red y vuelve a abrir la app. Se conservan las consultas previamente cargadas. En Android Studio, Database Inspector muestra la tabla `cache`. |
| REST | Consulta HTTPS a Open-Meteo, JSON con temperatura y precipitación de la cancha. Sin clave. | Canchas > Ver horarios > Ubicación y clima > Consultar clima. Requiere Internet. |
| Firebase | Authentication con correo/contraseña y Firestore compartido con reglas de acceso y transacciones. | Registro, perfil, reservas y gestión. Consola local o consola Firebase del proyecto propio. |
| Geolocalización | Ubicación puntual del teléfono, permiso aproximado o preciso, distancia en línea recta y apertura de la cancha en una app de mapas. | Detalle > Calcular distancia desde mi ubicación. Activa GPS y concede permiso; en el emulador configura una posición. |

La ubicación del teléfono no se sube a Firebase. La consulta meteorológica envía las coordenadas públicas de la cancha. Las ubicaciones del catálogo de prueba son demostrativas: el administrador puede editar latitud y longitud. La distancia no es una ruta de conducción. No se integra un SDK de mapas de pago.

## Probar localmente

Requisitos: Node.js 22, JDK 21 o superior para los emuladores Firebase, Android Studio y SDK 37. La compilación conserva la configuración Java/Gradle del proyecto original y se verificó con el JDK 25 de Android Studio.

1. Descomprime el paquete completo.
2. Desde su carpeta ejecuta `powershell -ExecutionPolicy Bypass -File .\Iniciar-Firebase.ps1`.
3. Espera los servicios listos; en otra terminal ejecuta `powershell -ExecutionPolicy Bypass -File .\Cargar-Demo.ps1`.
4. Abre la carpeta `android` en Android Studio y ejecuta la app en un dispositivo virtual. También puedes instalar el APK local en ese emulador.
5. Si Android 17 lo solicita, permite el acceso a la red local. El clima necesita Internet además de los servicios locales.

| Cuenta ficticia local | Contraseña |
| --- | --- |
| cliente@canchas.test | Canchas2026 |
| admin@canchas.test | Canchas2026 |

Consola local: http://127.0.0.1:4000. Usa Ctrl+C para detener los servicios; el iniciador exporta e importa los datos locales. Las cuentas demo solo existen en los emuladores. **No envíes dinero real a datos de prueba.**

Para un teléfono USB, usa `emulatorHost=127.0.0.1` en `android/firebase.properties`, `useEmulators=true`, recompila y ejecuta `adb reverse tcp:9099 tcp:9099` y `adb reverse tcp:8080 tcp:8080`. En el emulador Android se usa `10.0.2.2` por defecto.

## Funcionalidades conservadas

- Registro, inicio de sesión, recuperación por correo, edición de perfil y cambio de contraseña con reautenticación.
- Búsqueda y filtros de canchas, fechas futuras y horarios por hora en zona de Perú.
- Reserva atómica e identificador de reintento: dos clientes no pueden obtener el mismo horario activo.
- Retención de 15 minutos para enviar comprobante; revisión manual hasta 24 horas o hasta iniciar la reserva.
- Administración de canchas, tarifas, horarios, desactivación lógica, bloqueos, revisión de pagos y registro de devoluciones.
- Comprobantes privados comprimidos a menos de 200 KB en documentos independientes, sin Cloud Storage.

La app registra pagos y devoluciones **manuales**. No transfiere dinero ni comprueba depósitos automáticamente. Un administrador verifica el ingreso antes de confirmar.

## Funcionamiento sin conexión y vencimientos

SQLite es una caché de consulta y Firestore es la fuente compartida. El primer acceso requiere conexión. Se conservan datos previamente cargados de la cuenta; no se guardan contraseñas, tokens ni comprobantes en SQLite. El panel de reservas de otros usuarios no se almacena en SQLite. Cerrar sesión elimina la caché local.

Reservar, revisar pagos, editar datos compartidos y consultar disponibilidad actual requiere conexión. Las transacciones no se confirman sin servidor; la caché SQLite se actualiza con datos confirmados por el servidor.

No hay tarea programada de limpieza: las reservas pendientes vencidas aparecen como Vencida al consultar y el horario puede reutilizarse mediante transacción. Su documento histórico permanece. El tiempo se valida también con `request.time` en las reglas; si el reloj del teléfono está desajustado más de un minuto, la escritura se rechaza.

## Arquitectura

MVVM sencillo: `AppViewModel` coordina el estado; `FirebaseRepository` concentra autenticación, listeners y transacciones; `LocalCache` conserva consultas SQLite; `WeatherApi` consume REST y `DeviceLocation` obtiene una posición. Las pantallas Compose usan componentes compartidos. No se añadieron contenedores de inyección ni clases de caso de uso por cada botón.

Los archivos están en `android/app/src/main/java/com/example/canchasdeportivas/`. Las reglas y pruebas de integración están en `firebase/`.

## Coste y límites

Mantén el proyecto en Spark, sin cuenta de facturación vinculada. Auth por correo y Firestore tienen cuotas gratuitas; al agotarlas pueden rechazarse operaciones. No es un servicio ilimitado. Consulta [precios oficiales de Firebase](https://firebase.google.com/pricing).

Open-Meteo permite uso gratuito no comercial con límites; adecuado para esta demostración académica. La app muestra atribución. Revisa sus [condiciones y límites](https://open-meteo.com/en/pricing) si el proyecto pasa a uso comercial.

Consulta `VALIDACION.md` para resultados y `evidencias/` para capturas reales de prueba.
