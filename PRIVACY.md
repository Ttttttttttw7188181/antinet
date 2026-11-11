# AntiNet privacy policy

AntiNet is a local proxy client. It has no analytics, advertising or tracking
servers and does not send your configuration or traffic logs to the developer.

## What stays on the device

- Server profiles, subscriptions, routing rules, the selected server and app
  settings are stored in the app's private storage.
- The Shizuku permission is managed by the Shizuku app. AntiNet does not store
  a separate token.
- Logs are written locally and are only shared when you explicitly export them.

## Network connections

AntiNet connects only to the servers and subscription URLs that you add.
Subscription updates, delay tests and rule-set downloads request the URLs
configured in the app (by default `generate_204` endpoints, the subscription
URL and SagerNet rule-set files). Those services see your IP address and the
request headers, exactly as they would from a browser.

If you enable hotspot sharing, AntiNet asks the Shizuku user service on this
device to create a test network and start tethering. The Shizuku app follows
its own privacy policy. Traffic of hotspot clients is proxied by the core you
selected and is not sent anywhere else.

## Permissions

- `INTERNET` and network state: connect to proxies and subscriptions.
- `BIND_VPN_SERVICE` / foreground service: run the tunnel.
- `POST_NOTIFICATIONS`: show the foreground-service notification.
- `QUERY_ALL_PACKAGES`: let the per-app proxy picker list installed apps and
  let AntiNet detect Shizuku. The package list is not uploaded.
- `CAMERA`: scan QR codes. Frames are processed on the device and are not
  stored.

## Removing data

Uninstalling AntiNet removes its private storage. Exported backups and logs are
files that you created and must be deleted separately.

Last updated: 2026-11-11
