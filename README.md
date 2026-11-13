# Cyberware: Sandevistan (addon para Cyberware 1.12.2)

## Requisitos
- JDK 8 (ForgeGradle 2.3 NO funciona con Java 11/17/21)
- Gradle 4.9 (el wrapper de Forge MDK 1.12.2 ya lo trae)
- Forge 1.12.2-14.23.5.2860
- Jar de Cyberware 0.2.11.29 (ya incluido en `libs/`)

## Compilar
1. Descarga el MDK de Forge 1.12.2-14.23.5.2860 y copia de el `gradlew`, `gradlew.bat`
   y `gradle/wrapper/gradle-wrapper.jar` a esta carpeta.
2. `gradlew setupDecompWorkspace` (una sola vez, tarda un rato)
3. `gradlew build`

El jar queda en `build/libs/cyberware-sandevistan-1.0.0.jar`. Ponlo en `mods/` junto con Cyberware.

## Probar
`/give @p cyberware_sandevistan:sandevistan` y instalarlo en una mesa de cirugia (slot Hueso).

## Compilar en GitHub (sin instalar nada)
Sube el proyecto a un repositorio de GitHub; el workflow `.github/workflows/build.yml` compila solo.
El jar se descarga desde la pestana Actions > ultima ejecucion > Artifacts.
