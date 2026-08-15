# Релизы и установка на телевизор

## Подпись

Подпись APK бесплатна. Проект использует собственный Java KeyStore. Один и тот же
ключ нужно сохранять для всех будущих версий: Android разрешает обновление поверх
установленного приложения только при совпадении `applicationId` и ключа подписи.

Создать ключ один раз:

```bash
keytool -genkeypair -v \
  -keystore anion-tv-release.jks \
  -alias anion-tv \
  -keyalg RSA -keysize 4096 -validity 10000
```

Сделайте как минимум две резервные копии keystore и паролей. Потерянный ключ нельзя
восстановить, а сборкой с новым ключом нельзя штатно обновить уже установленное APK.

Для локальной release-сборки скопируйте `keystore.properties.example` в
`keystore.properties` и заполните значения. Оба файла с секретами — keystore и
`keystore.properties` — игнорируются Git.

## Секреты GitHub Actions

В Settings → Secrets and variables → Actions создайте:

- `ANDROID_KEYSTORE_BASE64` — base64-содержимое файла keystore;
- `ANDROID_KEYSTORE_PASSWORD` — пароль keystore;
- `ANDROID_KEY_ALIAS` — alias ключа, обычно `anion-tv`;
- `ANDROID_KEY_PASSWORD` — пароль ключа.

На macOS получить значение первого секрета можно так:

```bash
base64 -i anion-tv-release.jks | pbcopy
```

## Выпуск версии

Release workflow принимает строгие теги вида `vMAJOR.MINOR.PATCH`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Pipeline запускает тесты, собирает подписанные APK и AAB, проверяет подпись APK,
создаёт контрольные суммы и публикует GitHub Release. Для установки на телевизор
нужен файл `anion-tv-VERSION.apk`; AAB оставлен для возможной публикации в магазине.

## Установка без Google Play

Самый понятный вариант для пользователя:

1. Скачать APK из GitHub Releases на компьютер и скопировать на USB-накопитель.
2. На телевизоре разрешить выбранному файловому менеджеру установку неизвестных
   приложений.
3. Открыть APK с USB и подтвердить установку.

Если браузер телевизора умеет скачивать файлы, APK можно скачать прямо со страницы
GitHub Release и открыть из «Загрузок».

Для разработчика быстрее установка по ADB. После включения режима разработчика и
сетевой отладки на телевизоре:

```bash
adb connect TV_IP:5555
adb install -r anion-tv-VERSION.apk
```

На новых версиях Android TV перед `adb connect` может потребоваться беспроводное
сопряжение командой `adb pair TV_IP:PAIRING_PORT`.

Google Play для sideload не нужен. Если позже понадобится массовая публикация через
Google Play или неограниченная верифицированная дистрибуция, регистрация разработчика
оплачивается отдельно; это не связано со стоимостью самой криптографической подписи.
