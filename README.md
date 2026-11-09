# Las Flores — Control Interno (PWA)

Aplicación instalable para uso **local** en tablet Android. Incluye Briefings, Control de uniformes/implementos y Personal. La publicación en GitHub Pages solo distribuye los archivos de la aplicación; **no recibe registros de trabajadores ni respaldos**.

## Novedades 2.3.0 (rama `desarrollo`)

- **Tema claro y oscuro** (botón 🌙/☀️ en la cabecera o en «Configuración» → «Tema de la aplicación»): modo día (claro, como siempre) y modo noche (oscuro, con los mismos colores verde y dorado). La elección se guarda en este navegador y se mantiene al cerrar y reabrir la app.

## Novedades 2.2.0 (rama `desarrollo`)

- **Editor de tipos de incumplimiento** (botón ✏️ dentro de «Registrar incumplimientos»): crear, editar, eliminar, desactivar y reactivar tipos, y cambiar su categoría. Los incumplimientos se guardan por id estable y cada observación conserva la etiqueta que tenía al registrarse, así el historial no cambia aunque se renombre un tipo.
- **Control de entrega de materiales** (nueva pestaña «Entregas» en Control de uniformes): trapos, mandiles, camisas, tocas y otros materiales, con trabajador, fecha, cantidad, motivo y responsable; aviso si el trabajador recibió el mismo material en los últimos 30 días; devoluciones y anulaciones con historial; cantidades por trabajador y reportes en el libro Excel de Control de uniformes (hojas «Entregas» y «Entregas por trabajador»).
- Los datos y respaldos antiguos se migran automáticamente al abrir la app: los incumplimientos registrados como texto pasan a tipos con id y los respaldos JSON sin los campos nuevos se importan sin perder información.

## Publicación mediante GitHub Pages

1. Crea un repositorio llamado, por ejemplo, `las-flores-control`. Si es una cuenta GitHub Free, el repositorio debe ser **público** para publicar con GitHub Pages.
2. Sube **el contenido de esta carpeta** a la raíz de la rama `main` del repositorio (no subas carpetas con registros personales, Excel ni archivos JSON).
3. En el repositorio abre **Settings → Pages → Build and deployment → Deploy from a branch → main / (root) → Save**.
4. Cuando termine la publicación, abre `https://TU-USUARIO.github.io/las-flores-control/` en el navegador Chrome de tu tablet.
5. Desde el menú de Chrome, usa **Instalar aplicación** (o Añadir a pantalla principal, según la versión).

**Importante:** una web de GitHub Pages es pública aunque el repositorio sea privado. No agregar datos reales del personal al repositorio.

## Funcionamiento y guardado

- El código de la app se descarga de GitHub Pages. Los datos de trabajo están en `localStorage` del **navegador/perfil y origen web**; las copias de recuperación se intentan almacenar en IndexedDB del dispositivo. No existe servidor ni sincronización entre tablets.
- El modo **manual** puede dejar cambios sin guardar. Guarda antes de cerrar o actualizar.
- Cerrar el navegador normalmente no elimina los datos, pero borrar datos del sitio, desinstalar la app o perder la tablet puede hacerlo. Exporta respaldos JSON fuera de la tablet regularmente.
- Si anteriormente utilizabas el `.html` abierto como archivo local, **sus datos no migran automáticamente** al dominio github.io. Antes de cambiar, descarga un respaldo JSON desde la aplicación antigua y luego impórtalo en la nueva. Comprueba que aparecen los trabajadores y registros antes de dar por concluida la migración.
- Cambiar **el usuario, dominio o nombre del repositorio** puede cambiar el origen o la ubicación de la aplicación. Planifica la migración mediante respaldo JSON y conserva estable el dominio y la URL.

## Publicar una actualización

1. Actualiza los archivos de la aplicación y sube un commit a `main`.
2. Incrementa `VERSION` en `sw.js` siempre que cambies los archivos que se almacenan offline.
3. Al abrir o reconectar la app, se comprueba si hay una nueva versión. Cuando está descargada, aparece el aviso «Actualización disponible».
4. Pulsa **Guardar cambios** antes de **Actualizar aplicación** si trabajas con guardado manual.
5. Al aceptar la actualización, la PWA se reinicia con el código nuevo. Los datos locales del mismo navegador y origen no se sobrescriben por una actualización ordinaria.

**Si cambias la estructura de los datos**, crea migraciones compatibles. No cambies ni borres las claves `lasflores.control.v2` y `lasflores.control.prefs.v2` sin una migración segura.

## Verificación local

Para probar la PWA localmente, sirve la carpeta mediante un servidor web de desarrollo (por ejemplo `python -m http.server 8080`) y abre `http://localhost:8080/`. La instalación y los service workers no funcionan desde un archivo `file://`.

## Privacidad

No hay inicio de sesión: cualquier persona con acceso al navegador/perfil que contiene los datos podrá verlos. GitHub Pages distribuye **código público**, no protege con contraseña la app. Para una operación empresarial con varios usuarios/permisos, será necesario un sistema de autenticación y almacenamiento administrado aparte.
