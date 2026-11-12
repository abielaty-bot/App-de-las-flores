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

## Entrega 4 — Recordatorios y notificaciones
- [ ] Jornada, descanso, horario silencioso; recordatorios personalizados, recurrentes y únicos.
- [ ] Permisos, canales, alarmas, reinicio, cambios de sesión, posponer, abrir módulo/fecha, aviso de prueba, respaldo.

## Entrega 5 — Mi jornada
- [ ] Próxima actividad, progreso, pendientes inteligentes, historial de avisos.
- [ ] Integrar Uniformes, Funciones, Briefings, Incidentes y tareas manuales. Descartar notificación no completa tarea.

## Entrega 6 — Actualizaciones (adelantada a 4.0015, candidata)
- [x] Bytes reales, porcentaje, velocidad suavizada, tiempo transcurrido/estimado, tamaño y fecha de publicación.
- [x] Servicio de descarga en primer plano, notificación de progreso, abrir módulo desde aviso, cancelación explícita, reintento con HTTP Range validado, reinicio si no se acepta.
- [x] Espacio disponible, SHA-256, paquete, firma y versión antes de preparar y antes de instalar; historial de versiones detectadas, novedades, diagnóstico.
- [x] Comprobaciones periódicas opcionales, con conexión, aproximadamente diarias y sin descargar automáticamente.
- [ ] Compilación CI, pruebas de dispositivo y publicación firmada.
- Limitaciones: Android puede detener el servicio; la parte se conserva para reintentar. No se puede garantizar ETA exacta ni avisos con permisos desactivados. El historial comienza al incorporar esta versión y no inventa instalaciones antiguas.

## Compatibilidad
- No reescribir inspecciones ni asistencias anteriores. La aplicabilidad histórica anterior a v9 permanece como originalmente registrada.
- El formato JSON v7 se restaura en Android compatible; las versiones antiguas no pueden leerlo. El convertidor Web todavía no admite v6/v7 y debe bloquear fusiones incompatibles hasta actualizarse.
- Probar con datos sintéticos, restauración y actualización firmada antes de publicar. No publicar información privada de trabajadores.
