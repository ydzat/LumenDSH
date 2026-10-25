# Wireless ADB Development

## Verified local setup

- Device: Huawei nova 5T / YAL-L21, Android 10.
- ADB executable: `D:\Programs\MuMuPlayer\nx_main\adb.exe`.
- Last verified Wi-Fi endpoint: `<PHONE_IP>:5555`.
- Verification: 2026-10-24 at 20:02:13 CEST, after the user physically disconnected USB.
- `adb devices -l` listed only the network device with state `device`.
- A command explicitly addressed to that endpoint returned `WIRELESS_ADB_VERIFIED`, model `YAL-L21`, and the phone time.

The port was already configured as 5555 when inspected; this test did not need to enable TCP mode. The address is a local snapshot and may change after DHCP renewal or a network change. Reboot persistence has not been tested.

This is workstation-to-phone debugging on the local network. It does not implement remote DSH support and does not change the project's phone-local DSH scope.

## Connect and verify from PowerShell

Keep the workstation and phone on a mutually reachable local network.

```powershell
$adbPath = 'D:\Programs\MuMuPlayer\nx_main\adb.exe'
$phoneEndpoint = '<PHONE_IP>:5555'
& $adbPath connect $phoneEndpoint
& $adbPath devices -l
& $adbPath -s $phoneEndpoint shell echo WIRELESS_ADB_VERIFIED
& $adbPath -s $phoneEndpoint shell getprop ro.product.model
```

Require both device state `device` and a successful shell command. A remembered endpoint or a successful TCP connection alone is not sufficient evidence.

Always use `-s $phoneEndpoint` for subsequent wireless commands. `-d` selects USB and is not appropriate after unplugging it. If USB is also connected, explicitly selecting the endpoint proves which transport the test uses.

## Recover after reboot, IP change, or lost connection

1. Check the phone's current Wi-Fi address; do not assume the previous IP is still assigned to it.
2. Try reconnecting to that endpoint. For a stale connection, disconnect that endpoint and connect again; do not restart the entire ADB server unnecessarily.
3. If TCP ADB is no longer available, reconnect USB, unlock the phone, and accept the normal computer authorization prompt if required.
4. With exactly one USB phone connected, inspect its address and enable TCP mode:

```powershell
& $adbPath -d get-state
& $adbPath -d shell ip -4 addr show wlan0
& $adbPath -d tcpip 5555
# Set this to the address just inspected, rather than blindly reusing the example.
$phoneEndpoint = '<PHONE_IP>:5555'
& $adbPath connect $phoneEndpoint
& $adbPath -s $phoneEndpoint shell echo WIRELESS_ADB_VERIFIED
```

With multiple USB devices, replace `-d` with the intended USB serial. `tcpip` restarts the phone's ADB daemon and can briefly interrupt debugging; it does not require replacing DSH.

After connecting, unplug USB and repeat the explicit network shell command. If unreachable, inspect network isolation/routing before changing firewall or router settings. An `unauthorized` device requires the user's authorization on the phone; do not bypass it.

## Disconnect versus disable

Disconnect this workstation's network transport:

```powershell
& $adbPath disconnect $phoneEndpoint
```

This does not disable the phone's TCP listener. To return the reachable device to USB-only ADB mode:

```powershell
& $adbPath -s $phoneEndpoint usb
```

The wireless connection is expected to drop. Reconnect the cable for subsequent USB debugging. Use this only when intentionally ending wireless debugging.

Use this development listener on a trusted local network; no public port forwarding is required or configured by this procedure. ADB authorization keys are separate from DSH credentials and must remain private.

## Platform reference

Android 10 uses USB-initialized TCP ADB rather than the Android 11+ wireless-debugging pairing UI. See [Android's official ADB instructions](https://developer.android.com/tools/adb#wireless).
