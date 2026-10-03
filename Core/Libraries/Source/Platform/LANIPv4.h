// GeneralsX @bugfix Codex 04/10/2026 Share Android LAN selection and subnet validation.
#pragma once

#include <cstdint>
#include <cstring>

namespace GeneralsLAN
{
struct IPv4Interface
{
	char name[64] = {};
	uint32_t ip = 0; // All addresses are in host byte order.
	uint32_t netmask = 0;
	uint32_t broadcast = 0;
	unsigned int index = 0;
	int priority = 0;
};

inline bool startsWith(const char *name, const char *prefix)
{
	return std::strncmp(name, prefix, std::strlen(prefix)) == 0;
}

// GeneralsX @bugfix Codex 04/10/2026 Exclude Docker bridges without excluding Android tethering bridges.
// Upstream reference: beckren, PR #307 https://github.com/fbraz3/GeneralsX/pull/307
inline bool isDockerBridge(const char *name)
{
	if (!name || !startsWith(name, "br-") || std::strlen(name + 3) != 12)
		return false;
	for (const char *p = name + 3; *p; ++p)
		if (!((*p >= '0' && *p <= '9') || (*p >= 'a' && *p <= 'f') || (*p >= 'A' && *p <= 'F')))
			return false;
	return true;
}

inline bool isSoftAPInterface(const char *name)
{
	return name && (startsWith(name, "ap") || startsWith(name, "softap") ||
		startsWith(name, "swlan") || startsWith(name, "bridge") || startsWith(name, "br"));
}

inline int interfacePriority(const char *name)
{
	if (!name || startsWith(name, "rmnet") || startsWith(name, "r_rmnet") ||
		startsWith(name, "ccmni") || startsWith(name, "pdp") || startsWith(name, "wwan") ||
		startsWith(name, "tun") || startsWith(name, "tap") || startsWith(name, "ppp") ||
		startsWith(name, "ipsec") || startsWith(name, "wg") || startsWith(name, "v4-") ||
		startsWith(name, "clat") || startsWith(name, "dummy") || startsWith(name, "p2p") ||
		startsWith(name, "aware") || startsWith(name, "utun") || startsWith(name, "docker") ||
		startsWith(name, "veth") || startsWith(name, "virbr") || startsWith(name, "awdl") ||
		startsWith(name, "llw") || isDockerBridge(name))
		return 0;
	// A tethering bridge owns the AP's IPv4 on devices using bridged SoftAP.
	if (startsWith(name, "ap_br") || startsWith(name, "bridge") || startsWith(name, "br"))
		return 400;
	if (startsWith(name, "ap") || startsWith(name, "softap") || startsWith(name, "swlan"))
		return 300;
	if (startsWith(name, "wlan") || startsWith(name, "wifi") || startsWith(name, "wl"))
		return 200;
	if (startsWith(name, "eth"))
		return 100;
	return 0; // Unknown, cellular and virtual interfaces are not LAN candidates.
}

inline bool setSubnet(IPv4Interface &iface, uint32_t ip, uint32_t mask, uint32_t broadcast)
{
	const uint32_t hosts = ~mask;
	if (!ip || (ip >> 24) == 127 || (ip >> 24) == 0 || ip >= 0xe0000000u ||
		(ip & 0xffff0000u) == 0xa9fe0000u || !mask || hosts < 3 || (hosts & (hosts + 1)))
		return false;
	const uint32_t network = ip & mask;
	const uint32_t calculated = network | hosts;
	if (ip == network || ip == calculated)
		return false;
	iface.ip = ip;
	iface.netmask = mask;
	// Reject unset, limited, or inconsistent ifa_broadaddr; never assume /24.
	iface.broadcast = broadcast == calculated ? broadcast : calculated;
	return true;
}

inline bool preferInterface(const IPv4Interface &candidate, const IPv4Interface &selected)
{
	return candidate.priority > selected.priority ||
		(candidate.priority == selected.priority && candidate.priority > 0 &&
		 (std::strcmp(candidate.name, selected.name) < 0 ||
		  (std::strcmp(candidate.name, selected.name) == 0 && candidate.ip < selected.ip)));
}
}
