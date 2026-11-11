# AntiNet

AntiNet — VPN-клиент для Android с двумя ядрами на выбор: **Xray** и
**sing-box**. Интерфейс в духе Material You, профили и подписки, цепочки
серверов, правила маршрутизации, фрагментация для Xray и раздача VPN в точку
доступа через Shizuku — без root.

Пакет: `com.antinet.vpn`. Версия: 1.2.0.

> AntiNet не связан с INCY, v2rayNG, Shizuku или их авторами. Дизайн
> вдохновлён клиентом INCY, но его название, логотип и графика не
> используются. Пользуйтесь приложением только со своими серверами и
> подписками.

## Возможности

- **Два ядра.** Xray и sing-box переключаются в разделе «Ядро». Настройки
  показывают только то, что поддерживает выбранное ядро.
- **Протоколы.** VLESS (включая REALITY), VMess, Trojan, Shadowsocks,
  SOCKS5, HTTP. Для Xray также WireGuard и Hysteria2; собственные JSON
  outbound-ы поддерживаются обоими ядрами.
- **Цепочки.** Профиль может ссылаться на другие серверы: вход →
  промежуточный → выход.
- **Группы.** Виртуальный профиль с балансировщиком `leastPing`,
  `leastLoad` или `random` (Xray) и `urltest`/`selector` (sing-box).
- **Подписки.** Ссылка, QR-код, буфер обмена, base64 и plain-формат,
  ручное и автоматическое обновление.
- **Маршрутизация.** Весь трафик через прокси, обход России или Ирана,
  блокировка рекламы, локальные адреса напрямую, свои JSON-правила для
  Xray.
- **Фрагментация (Xray).** Дробление TLS ClientHello или всех TCP-пакетов:
  длина, интервал и максимальное число фрагментов. Помогает против DPI.
- **VPN для отдельных приложений.** Белый или чёрный список пакетов.
- **Статистика.** Скорость, трафик за сессию, время подключения.
- **Failover.** При смене Wi-Fi ↔ мобильная сеть туннель перезапускается
  автоматически.
- **Точка доступа.** Отдельный туннель для Wi-Fi-клиентов через Shizuku,
  без root и без Shizzi.
- **Резервная копия.** Экспорт и импорт профилей, подписок и настроек в
  JSON.

## Ядра

**Xray** — `libv2ray.aar` из
[AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) v26.10.31
(Xray-core). Поддерживает фрагментацию, mKCP, свои JSON-правила и
балансировщики Xray.

**sing-box** — `libbox.aar`
(`net.clever-vpn:libbox-android:2.1.5`, sing-box 1.12). Даёт гибкий DNS,
выбор стека TUN (`gvisor`, `system`, `mixed`), sniffing, rule-set и
современные транспорты. mKCP не поддерживается — для него нужно выбрать
Xray.

Профили, подписки, группы и цепочки общие для обоих ядер. При смене ядра
на активном подключении AntiNet перезапускает туннель.

## Точка доступа через Shizuku

Android не разрешает два VPN одновременно, поэтому раздача сделана иначе:

1. Shizuku запускает пользовательский сервис AntiNet с правами shell
   (uid 2000).
2. Сервис создаёт тестовый TUN `10.163.0.2/24` через скрытый
   `TestNetworkManager` и регистрирует его как тестовую сеть.
3. Вызывается `TetheringManager.setPreferTestNetworks(true)`, затем
   запускается Wi-Fi-раздача.
4. Дескриптор TUN передаётся в приложение.
5. Приложение поднимает **второе, независимое** ядро на этом дескрипторе.
   Основной VPN продолжает работать отдельно.

Что нужно:

- установленный и запущенный [Shizuku](https://shizuku.rikka.app/);
- разрешение Shizuku для AntiNet;
- включённый тумблер «Раздавать VPN» на экране «Точка доступа».

Root не нужен. Скрытые API и порядок вызовов проверены по AOSP
(`TestNetworkManager`, `TetheringManager`, `UpstreamNetworkMonitor`), но
конкретная прошивка может ограничивать тестовые сети или саму раздачу.
Некоторые производители требуют подтверждения включения точки доступа.

## Сборка

Нужны JDK 21, Android SDK 37 (platform и build-tools) и интернет: оба AAR
скачиваются при сборке и в репозитории не хранятся.

```bash
# 1. Xray
mkdir -p app/libs
curl -fL -o /tmp/libv2ray-full.aar \
  https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.10.31/libv2ray.aar
rm -rf /tmp/libv2ray && mkdir -p /tmp/libv2ray
(cd /tmp/libv2ray && unzip -q /tmp/libv2ray-full.aar && zip -q -d classes.jar 'go/*')
(cd /tmp/libv2ray && zip -q -r /tmp/libv2ray.aar .)
mv /tmp/libv2ray.aar app/libs/libv2ray.aar

# 2. sing-box
curl -fL -o app/libs/libbox.aar \
  https://repo1.maven.org/maven2/net/clever-vpn/libbox-android/2.1.5/libbox-android-2.1.5.aar

# 3. Тесты и APK
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug -PABI_FILTERS='arm64-v8a;x86_64'
```

Зачем удалять `go/*` из `libv2ray.aar`: оба AAR содержат сгенерированный
рантайм `go.Seq`. Копия из libbox — надмножество (есть `destroyRef`),
поэтому дубликат из libv2ray удаляется, иначе сборка падает на повторяющихся
классах.

Готовый APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

CI (GitHub Actions) делает то же самое при каждом push в `main`/`master` и
публикует `AntiNet-arm64-v8a-debug.apk` в релиз `ci-arm64`.

## Структура

- `engine/AntiNetVpnService.kt` — VPN-сервис, выбор ядра, основное и
  хотспотное подключения.
- `engine/XrayEngine.kt` — Xray через `CoreController`, счётчики трафика.
- `engine/SingBoxEngine.kt` — sing-box через libbox `CommandServer`,
  `PlatformInterface` и счётчики соединений.
- `engine/XrayConfigBuilder.kt`,
  `engine/SingBoxConfigBuilder.kt` — сборка конфигураций.
- `engine/TunFactory.kt` — основной TUN через `VpnService.Builder`.
- `shizuku/ShizukuUserService.kt` — тестовая сеть и раздача от имени shell.
- `shizuku/ShizukuBridge.kt` — привязка пользовательского сервиса.
- `hotspot/HotspotBridge.kt` — состояние раздачи на стороне приложения.
- `ui/` — Compose-интерфейс, экраны и тема.

## Ограничения

- Одновременная работа двух ядер (VPN + точка доступа) не проверена на
  всех прошивках и расходует больше памяти.
- Скрытые API раздачи могут быть заблокированы производителем.
- sing-box не поддерживает mKCP и фрагментацию Xray.
- Встроенной агрегации каналов нет.
- Приложение не проверяет сертификаты ваших прокси-серверов за вас:
  используйте `allowInsecure` только осознанно.

## Лицензия

GPL-3.0. Сторонние компоненты:

- Xray-core — MPL-2.0, XTLS;
- AndroidLibXrayLite — LGPL-3.0, 2dust;
- sing-box — GPL-3.0, SagerNet;
- libbox-android — GPL-3.0, clever-vpn;
- Shizuku API — Apache-2.0, RikkaApps;
- HiddenApiBypass — Apache-2.0, LSPosed;
- Jetpack Compose, Material 3, CameraX — Apache-2.0, Google/AOSP;
- OkHttp — Apache-2.0, Square;
- Gson — Apache-2.0, Google;
- ZXing — Apache-2.0;
- geoip/geosite — Loyalsoldier, SagerNet.
