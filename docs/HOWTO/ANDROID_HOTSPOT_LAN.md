# Android hotspot LAN verification

Install the same LAN release APK on all three arm64 Android devices. Open the game after starting the hotspot and connecting its clients.

1. Phone A: enable the Wi-Fi hotspot; connect B and C to it. Keep mobile data enabled for this test to verify that discovery ignores the cellular default route.
2. Open LAN Gaming on A, B and C. All three player names must appear automatically.
3. Create a game on A; confirm that B and C see it and can join.
4. Return all phones to the LAN lobby. Create a game on B; confirm that A and C see it and can join.
5. Start a game in both host configurations and check that gameplay packets work as well as discovery.
6. Repeat with all phones connected to an ordinary Wi-Fi router.

If discovery fails, capture logcat from each phone while entering LAN Gaming:

```sh
adb -s DEVICE_SERIAL logcat -v threadtime GeneralsLAN:D '*:S'
```

Expected diagnostics:

- `selected interface=... IP=... netmask=... broadcast=...` identifies the Wi-Fi/SoftAP interface. A may expose `ap0`, `ap_br_wlan0`, `br0`, `swlan0` or `wlan0`; B/C normally expose `wlan0`. No cellular/VPN address should be selected.
- `Discovery bound to 0.0.0.0:8086` confirms broadcast reception, with an explicit egress interface index.
- `LAN broadcast` and `UDP discovery send` show the actual subnet broadcast and port 8086.
- `UDP discovery receive` shows sender, destination, interface index and byte count; `LAN packet` shows an accepted game protocol message.
- `Wi-Fi multicast lock acquired` appears while the game is foregrounded and `released` when it is stopped.

The APK is a native Release build signed with a CI development certificate for sideloading. It does not include original game data. A different certificate from an existing installed build requires a separate installation; preserve imported game data before replacing an existing app.

The Linux CI socket test verifies the actual IP_PKTINFO send/receive helpers and discard handling, but does not substitute for this physical A/B/C test or OEM hotspot firmware behavior.
