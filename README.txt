ClearYourTV — лаунчер для Android TV / Google TV. Без рекламы и аналитики.

1. Открой папку в Android Studio, дождись Gradle Sync, затем Build > Build APK(s).
2. На ТВ: Настройки > Об устройстве > 7 раз нажать "Сборка" > Для разработчиков > Отладка по сети.
3. adb connect IP_ТВ:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
4. Сделать главным экраном, если Home не предлагает выбор:
   adb shell cmd package set-home-activity app.clearyourtv/.MainActivity
Вернуть стоковый: adb uninstall app.clearyourtv

Своя картинка-обои:
   adb push wallpaper.jpg /sdcard/Android/data/app.clearyourtv/files/wallpaper.jpg
   затем Настройки > Вид и сцены > Выбрать свою картинку.

Кнопки: MENU на пульте — Пункт управления; "Управление" справа вверху — то же самое.


=== v0.2 ===
Next stage: TV home screen redesign, cards, favorites, remote navigation.

Очистка (v1.1): плитка с кольцом ОЗУ в Dock или "Очистка" в Пункте управления.
   Ускорить — закрыть фон; Освободить место — старые приложения (OK — кэш, удержание OK — удалить).

v1.2: недавние приложения над Dock; в Пункте управления (MENU) — громкость (← →), входы HDMI, чёрный экран.

v1.3: главный экран в стиле Apple TV — полка карточек с подъёмом и подписью при фокусе.

v1.4: живой фон, ambient-подсветка под цвет приложения, блик на карточках, каскадное появление.

v1.5: Dock-капсула как на iPhone, блокировка рекламы и трекеров (DNS-фильтр), лаунчер по умолчанию, больше настроек.


=== v2.0-dev: Smart Device Setup ===
Первый запуск предлагает выбрать профиль ТВ: автоопределение, Android TV, Google TV,
Сбер ТВ/Салют ТВ, YaOS, другая Android-прошивка, Samsung Tizen, LG webOS или VIDAA.
Профиль можно изменить: Настройки → Устройство и Smart Cleaner.

Диагностика показывает производителя, модель, Android/API, Leanback/Live TV,
свободное место, ОЗУ, сеть и текущий HOME. Smart Cleaner выдаёт рекомендации,
но не удаляет системные приложения, службы, драйверы и прошивку.

Важно: APK-версия работает только в Android-среде. Tizen/webOS/VIDAA требуют
отдельных приложений для магазинов соответствующих производителей. На Сбер ТВ
и YaOS доступность HOME и установка APK зависят от модели и версии прошивки.

2.1-dev additions:
- Search installed apps by visible name or package id in the All Apps panel.
- Expanded Quick Controls shortcuts for display, sound, apps, date/time,
  accessibility, Wi-Fi and Bluetooth settings.
- Extended read-only device diagnostics: display metrics, CPU ABI/core count,
  RAM, storage and battery level where available.
- No automatic modification or removal of system components.
See UPDATE_v2.1-dev.txt for details and limitations.


=== v2.2-dev: ClearYourTV Branding ===
- Replaced Android launcher icon with the supplied square ClearYourTV artwork.
- Replaced Android TV / Leanback launcher banner with the supplied 16:9 artwork
  carrying the tagline “Чистый телевизор — без рекламы!”.
- Generated launcher icon assets for mdpi through xxxhdpi and TV banner
  resources for nodpi, xhdpi and xxhdpi.
- AndroidManifest references @mipmap/ic_launcher and @drawable/banner, so the
  new artwork is used by Android launchers that support these metadata fields.


=== v2.3-dev: Primary Brand Icon ===
- The latest supplied square ClearYourTV artwork is now the primary Android launcher icon.
- All launcher density resources and the in-app Settings logo use the same artwork.
- The 16:9 TV banner from v2.2-dev remains the Android TV banner.


=== v2.4-dev: Updated ClearYourTV TV Banner ===
- Replaced the Leanback banner with the latest supplied wide artwork.
- Banner master: assets/ClearYourTV_banner_master.jpg (1536x864).
- Android resources: drawable-nodpi, drawable-xhdpi and drawable-xxhdpi/banner.png.
- Manifest uses @drawable/banner; square launcher icon remains unchanged.
