AlarmaCalendarica
=================

Autor: Oliver Consterla (@Alderan-Smile).

Descripción
-----------
AlarmaCalendarica es una app para gestionar alarmas que consideran feriados (oficiales y personalizados). Este repositorio contiene la app Android y la base de datos local (Room).

Resumen de cambios recientes (entregados)
-----------------------------------------
- Corregido solapamiento de UI con la barra de estado y la barra de navegación en las pantallas de calendario y gestión de feriados personalizados: ahora las Activities usan WindowInsets para ajustar padding del contenido.
- Evitar pérdida de feriados personalizados al actualizar/guardar feriados oficiales:
  - Se eliminó `fallbackToDestructiveMigration()` en `AppDatabase` para prevenir borrados automáticos de la DB.
  - Al guardar feriados oficiales ahora se eliminan únicamente los feriados "no personalizados" del año/pais (`deleteNonCustomByCountryAndYear`), preservando feriados creados por el usuario.
  - Cambiada la estrategia de inserción en `HolidayDao` a `OnConflictStrategy.IGNORE` para evitar sobrescribir/exceder datos existentes.
- Dedupe en la inserción por rango: al insertar un rango de feriados personalizados, la app evita insertar duplicados exactos (fecha+nombre).
- Añadidos handlers UI para evitar solapamientos en `CustomHolidayActivity` y `CustomHolidayListActivity`.