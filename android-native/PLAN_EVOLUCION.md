# Plan de evolución Las Flores Android

Base publicada: 4.0014 (Personal, Uniformes y menú). No desinstalar la aplicación para actualizar.

## Entrega 1 — Briefings y datos (4.0013, publicada)
- [x] Calendario, sesiones flexibles, horas, convocados y seguimiento presencial.
- [x] Room v8, respaldo v6, reportes, CI y APK firmada.

## Entregas 2 y 3 — Personal, Uniformes y navegación (4.0014, publicada)
- [x] Programación regular, fines de semana (sábado/domingo/ambos), días personalizados y apoyos ocasionales. Excepciones por fecha.
- [x] Historial de cambios de programación a partir de esta versión; los datos antiguos no permiten reconstruir cambios previos ni asistencia real.
- [x] Perfil de uniforme seleccionable; sin inferir perfiles de nombres.
- [x] Catálogo por puesto y perfil; moñera femenina por defecto, configurable; perfiles desconocidos no eximen automáticamente.
- [x] Aplicabilidad congelada en inspección nueva, No aplica manual justificado y auditado; no computa como cumplimiento ni falta.
- [x] Uniformes: diseño borgoña/compacto, filtros, calendario, ocultar exenciones, siguiente trabajador; dos paneles en horizontal amplio, lista desplegable en vertical.
- [x] Menú modal global inspirado en las capturas del usuario: botón superior, cierre al seleccionar/tocar fuera/atrás, sin barra fija.
- [x] Migración Room v8→v9, respaldo v7, reportes y pruebas de regresión.
- [x] CI y publicación de APK firmada; prueba visual/funcional en tablet pendiente.

## Entregas 4–5 — Recordatorios y Mi jornada (4.0016, candidata)
- [x] Jornada, descanso, horario silencioso; recordatorios personalizados, recurrentes, únicos y plantillas editables.
- [x] Permisos, canales, alarmas exactas si autorizadas, aproximadas en caso contrario; reinicio, cambios de sesión, posponer, abrir módulo/fecha/sesión y aviso de prueba.
- [x] Mi jornada: próxima actividad, progreso, pendientes, vencidos, completados, campana, historial de avisos.
- [x] Integración con Uniformes, Funciones, Briefings, Incidentes y tareas manuales. Descartar notificación no completa tarea.
- [x] Room 9→10, respaldo JSON v8, reportes de actividades persistidas y avisos.
- [ ] Compilación, pruebas de permisos y reinicio en tablet, publicación firmada.
- Las alarmas se activan explícitamente en Ajustes. Los avisos antiguos no se envían en masa; los pendientes permanecen en Mi jornada. Las tareas de ventas y abastecimiento siguen siendo manuales hasta tener sus propios módulos.

## Entrega 6 — Actualizaciones (adelantada a 4.0015, candidata)
- [x] Bytes reales, porcentaje, velocidad suavizada, tiempo transcurrido/estimado, tamaño y fecha de publicación.
- [x] Servicio de descarga en primer plano, notificación de progreso, abrir módulo desde aviso, cancelación explícita, reintento con HTTP Range validado, reinicio si no se acepta.
- [x] Espacio disponible, SHA-256, paquete, firma y versión antes de preparar y antes de instalar; historial de versiones detectadas, novedades, diagnóstico.
- [x] Comprobaciones periódicas opcionales, con conexión, aproximadamente diarias y sin descargar automáticamente.
- [ ] Compilación CI, pruebas de dispositivo y publicación firmada.
- Limitaciones: Android puede detener el servicio; la parte se conserva para reintentar. No se puede garantizar ETA exacta ni avisos con permisos desactivados. El historial comienza al incorporar esta versión y no inventa instalaciones antiguas.

## Compatibilidad
- No reescribir inspecciones ni asistencias anteriores. La aplicabilidad histórica anterior a v9 permanece como originalmente registrada.
- El formato JSON v8 se restaura en Android compatible; las versiones antiguas no pueden leerlo. El convertidor Web todavía no admite v6/v7/v8 y debe bloquear fusiones incompatibles hasta actualizarse.
- Probar con datos sintéticos, restauración y actualización firmada antes de publicar. No publicar información privada de trabajadores.
