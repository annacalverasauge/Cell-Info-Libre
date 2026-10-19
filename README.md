# Cell Info Libre

Visor de información de red móvil al estilo de Network Cell Info Lite:
sin anuncios, sin rastreadores, sin librerías de terceros y **sin permiso de Internet**.

## Qué muestra
- Operador, tipo de red (2G/3G/4G/5G) y hora de la última lectura.
- Gráfica en tiempo real de la señal de la celda principal (verde/amarillo/naranja/rojo).
- Celda principal: MCC-MNC, TAC/LAC, CI, eNB + sector, PCI/PSC/BSIC, EARFCN/ARFCN
  con banda y frecuencia calculadas, ancho de banda, RSRP, RSRQ, SINR, CQI, RSSI,
  Timing Advance con distancia aproximada a la antena, NR (5G) SS-RSRP/RSRQ/SINR.
- Celdas vecinas y aviso de posible 5G NSA.
- Refresco cada 2 segundos; la pantalla se mantiene encendida mientras está abierta.

## Compilar
**Opción A – Android Studio:** File → Open → esta carpeta → botón Run (o Build → Build APK).

**Opción B – Sin instalar nada (GitHub):** sube la carpeta a un repositorio de GitHub.
La pestaña *Actions* compila sola y deja el APK en *Artifacts* → `CellInfoLibre-apk`.

Compatible con Android 5.0 (API 21) en adelante.

## Notas
- Android exige permiso de **Ubicación** (y tenerla activada) para leer datos de antenas.
- Algunos datos (vecinas, SINR, bandas) dependen de lo que el fabricante/módem exponga;
  si aparece "—" es que el teléfono no lo reporta.
- Todo el código es legible en `app/src/main/java/com/libre/cellinfo/` (3 archivos).
