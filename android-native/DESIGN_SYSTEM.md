# Las Flores — Sistema visual Android (4.0009)

## Referencia aprobada
Mockup vertical de cuatro pantallas (Inicio, Briefings, Uniformes, Reportes).
La imagen es una referencia, **no representa datos reales, fotografías de personal
ni sesiones adicionales**. Mantener el tulipán rojo sin sustituir la identidad.

## Paleta
| Propósito | HEX | Uso |
|---|---|---|
| Borgoña principal | #672638 | Botones principales, selección y encabezados |
| Ciruela profundo | #351C27 | Menú lateral y barras superiores |
| Champán | #C49A55 | Detalles decorativos, no texto pequeño sobre fondo claro |
| Marfil | #F8F5F0 | Fondo general |
| Carbón | #25212A | Texto de lectura |
| Éxito | #216B48 | Cumplimiento registrado |
| Advertencia | #9A6210 | Pendiente/atendiendo mesa |
| Error | #B3261E | No cumplió o error confirmado |
| Neutro | #75666C | Sin revisar o estados no determinados |

No representar incumplimientos con el borgoña de marca. No usar solo
color: cada estado debe tener etiqueta textual.

## Navegación responsive
- Tablet en **vertical**: barra inferior (Inicio, Personal, Briefings, Uniformes, Más).
  "Más" abre Funciones, Incidentes, Reportes, Respaldos y Actualizaciones.
- Tablet **horizontal** de >= 760dp de ancho: menú lateral permanente
  con las nueve secciones.
- Todas las secciones comparten esquinas, tipografía, márgenes y colores.
- Los submenús se presentan en una fila horizontal desplazable, no en varias
  filas que resten espacio al registro.
- El botón Atrás regresa a Inicio; al cerrar el menú Más se mantiene la pantalla.

## Inicio real, sin datos de ejemplo
- Programados hoy: reglas actuales de Personal, descansos, apoyos y vacaciones.
- Briefings por informar: solamente registros de tipo MESA sin comunicación.
  Si la fecha seleccionada en Briefings no es hoy, no se presenta un cero falso.
- Uniformes observados: resultado **inicial** de inspecciones finalizadas hoy;
  una corrección no borra su antecedente.
- Funciones: solo asignaciones del día no anuladas.
- Incidentes abiertos: registrados, en atención o pendientes de verificación.
- Sin registrar nunca significa que el trabajador faltó o incumplió.

## Comportamiento táctil
- Accesos de tamaño adecuado para tablet: Material 3 y superficie >=48dp.
- En vertical, el checklist de uniformes usa una fila compacta por requisito,
  con botones Sí/No y acciones de corrección posteriores.
- Briefings mantiene confirmaciones, informa al personal que estuvo en mesa
  y permite revertir un marcado equivocado con trazabilidad.
- Pestañas en carrusel horizontal con texto completo y selección visible.

## Conservación y seguridad de datos
- Mantener Room en versión 7: ningún cambio de tablas ni migración destructiva.
- Conservar package .beta, firma del canal y actualización in-app.
- Preservar JSON v5, Excel de 19 hojas y evidencias históricas.
- No introducir fotografías ficticias de empleados.
- Revisar casos con nombres largos, 0 registros, errores de red, vista grande,
  modo vertical, modo horizontal y tamaño de fuente elevado.
- No publicar una actualización firmada hasta que CI pase en el commit final.
- Antes de instalar en la tablet, generar respaldo JSON y verificar la
  actualización **sin desinstalar**.
