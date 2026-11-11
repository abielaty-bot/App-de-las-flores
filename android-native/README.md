# Las Flores Android 4.0004 — Funciones

Código Android Kotlin/Compose, Room/SQLite offline-first, canal firmado Github Releases.

## Funciones incluidas
- Catálogo inicial de la web: Oficina I (Caja, Estrado, Entrada, Ventana, C, Vasos);
  Oficina II (Caja, Entrada, O1, O2, T1, T2); generales (Manteles, Heladería,
  Desperdicios, Jabas y utensilios, Exhibidor de vinos).
- El catálogo es editable, permite desactivar sin perder historial y crear funciones
  mensuales. Los textos específicos de cada puesto pueden ajustarse sin crear APK.
- Registro de responsables, estados, observaciones, correcciones, anulaciones y auditoría.
- Evidencias: hasta 2 fotos JPEG comprimidas por asignación, registradas en SQLite.
- *Confirmar envío al grupo* es un registro manual declarativo; no envía mensajes por
  WhatsApp ni certifica que el grupo haya recibido la imagen.
- Cumplimiento mensual: Cumplió / (Cumplió + No cumplió). Sin valor cuando no hay
  evaluaciones. Las anulaciones y pendientes no computan.
- Respaldos JSON Android formato 4, incluyendo fotografías; importación de versiones 1-3.
- Room v6 y migración 5→6 no destructiva.

## Pendientes antes de publicar
- CI compilación, tests y APK firmada.
- Probar actualización en tablet 4.0003→4.0004, conservación del historial y migración.
- Probar fotos, restauración y envío manual en condiciones reales.
- Exportación Excel y migración de web completa en una versión posterior.
