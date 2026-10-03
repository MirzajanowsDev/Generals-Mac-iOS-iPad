// GeneralsX @bugfix Codex 04/10/2026 Regression coverage for Android Wi-Fi/SoftAP selection.
#include "Platform/LANIPv4.h"
#include <cassert>
#include <cstdio>

int main()
{
	using namespace GeneralsLAN;
	const char *rejected[] = {"rmnet_data0", "r_rmnet_data0", "ccmni0", "pdp0", "wwan0",
		"tun0", "tap0", "ppp0", "ipsec0", "wg0", "v4-wlan0", "clat4", "dummy0",
		"p2p0", "aware0", "utun0", "docker0", "vethabcd", "virbr0", "awdl0", "llw0",
		"br-ffd9ee17a8f4", "br-e7FBC3f89baB", "unknown0", "lo"};
	for (const char *name : rejected)
		assert(interfacePriority(name) == 0);
	assert(interfacePriority(nullptr) == 0);
	assert(interfacePriority("br0") > interfacePriority("wlan0"));
	assert(interfacePriority("br-lan") > 0);
	assert(interfacePriority("ap_br_wlan0") > interfacePriority("wlan0"));
	assert(interfacePriority("ap0") > interfacePriority("wlan0"));
	assert(interfacePriority("swlan0") > interfacePriority("wlan0"));
	assert(interfacePriority("wlan0") > interfacePriority("eth0"));
	assert(interfacePriority("wlp1s0") > 0);
	assert(!isDockerBridge("br-ffd9ee17a8f"));
	assert(!isDockerBridge("br-ffd9ee17a8fg"));

	IPv4Interface iface;
	assert(setSubnet(iface, 0xc0a82b01u, 0xffffff00u, 0xc0a82bffu));
	assert(iface.broadcast == 0xc0a82bffu);
	assert(setSubnet(iface, 0xc0a82b01u, 0xffff0000u, 0xffffffffu));
	assert(iface.broadcast == 0xc0a8ffffu);
	assert(setSubnet(iface, 0xac140701u, 0xfffff000u, 0));
	assert(iface.broadcast == 0xac140fffu);
	assert(setSubnet(iface, 0xc0a82b01u, 0xfffffe00u, 0xc0a82affu));
	assert(iface.broadcast == 0xc0a82bffu);
	assert(setSubnet(iface, 0xc0a82b21u, 0xfffffff0u, 0));
	assert(iface.broadcast == 0xc0a82b2fu);
	assert(!setSubnet(iface, 0, 0xffffff00u, 0));
	assert(!setSubnet(iface, 0x7f000001u, 0xff000000u, 0));
	assert(!setSubnet(iface, 0xa9fe0101u, 0xffff0000u, 0));
	assert(!setSubnet(iface, 0xe0000001u, 0xffffff00u, 0));
	assert(!setSubnet(iface, 0xc0a82b00u, 0xffffff00u, 0));
	assert(!setSubnet(iface, 0xc0a82bffu, 0xffffff00u, 0));
	assert(!setSubnet(iface, 0xc0a82b01u, 0, 0));
	assert(!setSubnet(iface, 0xc0a82b01u, 0xff00ff00u, 0));
	assert(!setSubnet(iface, 0xc0a82b01u, 0xfffffffeu, 0));
	assert(!setSubnet(iface, 0xc0a82b01u, 0xffffffffu, 0));

	IPv4Interface wifi, ap, selected;
	std::strcpy(wifi.name, "wlan0");
	wifi.ip = 0xc0a80105u;
	wifi.priority = interfacePriority(wifi.name);
	std::strcpy(ap.name, "ap0");
	ap.ip = 0xc0a82b01u;
	ap.priority = interfacePriority(ap.name);
	assert(preferInterface(wifi, selected));
	assert(preferInterface(ap, wifi));
	assert(!preferInterface(wifi, ap));
	std::puts("LAN IPv4 policy: all cases passed");
}
