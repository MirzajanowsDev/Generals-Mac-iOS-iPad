// GeneralsX @bugfix Codex 04/10/2026 Enumerate Wi-Fi/SoftAP independently of Android's cellular default route.
#pragma once

#ifdef __ANDROID__
#include "LANIPv4.h"
#include <android/log.h>
#include <arpa/inet.h>
#include <cerrno>
#include <ifaddrs.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

namespace GeneralsLAN
{
// Upstream reference: fbraz3, PR #313 https://github.com/fbraz3/GeneralsX/pull/313
// Keep OS enumeration and socket controls in the platform layer, shared by all LAN callers.
inline void logInterface(const IPv4Interface &iface, const char *state)
{
	char ip[INET_ADDRSTRLEN], mask[INET_ADDRSTRLEN], broadcast[INET_ADDRSTRLEN];
	in_addr addr;
	addr.s_addr = htonl(iface.ip);
	inet_ntop(AF_INET, &addr, ip, sizeof(ip));
	addr.s_addr = htonl(iface.netmask);
	inet_ntop(AF_INET, &addr, mask, sizeof(mask));
	addr.s_addr = htonl(iface.broadcast);
	inet_ntop(AF_INET, &addr, broadcast, sizeof(broadcast));
	__android_log_print(ANDROID_LOG_INFO, "GeneralsLAN",
		"%s interface=%s index=%u IP=%s netmask=%s broadcast=%s priority=%d",
		state, iface.name, iface.index, ip, mask, broadcast, iface.priority);
}

inline void considerInterface(IPv4Interface &selected, const char *name, unsigned int flags,
	uint32_t ip, uint32_t mask, uint32_t broadcast)
{
	IPv4Interface candidate;
	std::strncpy(candidate.name, name ? name : "", sizeof(candidate.name) - 1);
	candidate.ip = ip;
	candidate.netmask = mask;
	candidate.broadcast = broadcast;
	candidate.priority = interfacePriority(name);
	// GeneralsX @bugfix Codex 04/10/2026 Require a carrier for station/Ethernet links.
	// Some SoftAP drivers expose an UP broadcast interface without IFF_RUNNING.
	if (!(flags & IFF_UP) || !(flags & IFF_BROADCAST) ||
		(flags & (IFF_LOOPBACK | IFF_POINTOPOINT)) || !candidate.priority ||
		(!(flags & IFF_RUNNING) && !isSoftAPInterface(name)) ||
		!setSubnet(candidate, ip, mask, broadcast))
	{
		logInterface(candidate, "rejected");
		__android_log_print(ANDROID_LOG_INFO, "GeneralsLAN", "interface=%s flags=0x%x", candidate.name, flags);
		return;
	}
	candidate.index = if_nametoindex(name);
	if (!candidate.index)
	{
		logInterface(candidate, "rejected (no index)");
		return;
	}
	logInterface(candidate, "candidate");
	if (preferInterface(candidate, selected))
		selected = candidate;
}

inline bool selectInterface(IPv4Interface &selected)
{
	selected = IPv4Interface();
	ifaddrs *addresses = nullptr;
	if (getifaddrs(&addresses) == 0)
	{
		for (const ifaddrs *ifa = addresses; ifa; ifa = ifa->ifa_next)
		{
			if (!ifa->ifa_addr || ifa->ifa_addr->sa_family != AF_INET)
				continue;
			uint32_t mask = 0, broadcast = 0;
			if (ifa->ifa_netmask && ifa->ifa_netmask->sa_family == AF_INET)
				mask = ntohl(reinterpret_cast<const sockaddr_in *>(ifa->ifa_netmask)->sin_addr.s_addr);
			if ((ifa->ifa_flags & IFF_BROADCAST) && ifa->ifa_broadaddr && ifa->ifa_broadaddr->sa_family == AF_INET)
				broadcast = ntohl(reinterpret_cast<const sockaddr_in *>(ifa->ifa_broadaddr)->sin_addr.s_addr);
			considerInterface(selected, ifa->ifa_name, ifa->ifa_flags,
				ntohl(reinterpret_cast<const sockaddr_in *>(ifa->ifa_addr)->sin_addr.s_addr), mask, broadcast);
		}
		freeifaddrs(addresses);
	}
	else
		__android_log_print(ANDROID_LOG_WARN, "GeneralsLAN", "getifaddrs failed: %s; trying IPv4 ioctl", strerror(errno));

	// IPv4 ioctl fallback does not require /proc/net, root, or RTM_GETLINK access.
	if (!selected.ip)
	{
		const int fd = socket(AF_INET, SOCK_DGRAM, 0);
		if (fd >= 0)
		{
			std::vector<ifreq> entries(32);
			ifconf config = {};
			bool complete = false;
			while (entries.size() <= 4096)
			{
				config.ifc_len = static_cast<int>(entries.size() * sizeof(ifreq));
				config.ifc_req = entries.data();
				if (ioctl(fd, SIOCGIFCONF, &config) < 0)
					break;
				if (config.ifc_len + static_cast<int>(sizeof(ifreq)) < static_cast<int>(entries.size() * sizeof(ifreq)))
				{
					complete = true;
					break;
				}
				entries.resize(entries.size() * 2);
			}
			if (complete)
			{
				for (int i = 0; i < config.ifc_len / static_cast<int>(sizeof(ifreq)); ++i)
				{
					ifreq query = entries[i];
					const uint32_t ip = ntohl(reinterpret_cast<const sockaddr_in *>(&query.ifr_addr)->sin_addr.s_addr);
					if (ioctl(fd, SIOCGIFFLAGS, &query) < 0)
						continue;
					const unsigned int flags = static_cast<unsigned short>(query.ifr_flags);
					if (ioctl(fd, SIOCGIFNETMASK, &query) < 0)
						continue;
					const uint32_t mask = ntohl(reinterpret_cast<const sockaddr_in *>(&query.ifr_netmask)->sin_addr.s_addr);
					uint32_t broadcast = 0;
					if ((flags & IFF_BROADCAST) && ioctl(fd, SIOCGIFBRDADDR, &query) == 0)
						broadcast = ntohl(reinterpret_cast<const sockaddr_in *>(&query.ifr_broadaddr)->sin_addr.s_addr);
					considerInterface(selected, entries[i].ifr_name, flags, ip, mask, broadcast);
				}
			}
			else
				__android_log_print(ANDROID_LOG_ERROR, "GeneralsLAN", "IPv4 ioctl enumeration failed: %s", strerror(errno));
			close(fd);
		}
	}
	if (selected.ip)
		logInterface(selected, "selected");
	else
		__android_log_print(ANDROID_LOG_ERROR, "GeneralsLAN", "No active LAN/hotspot IPv4 interface; refusing cellular/hostname fallback");
	return selected.ip != 0;
}

inline int configureDiscoverySocket(int fd, uint32_t ip, unsigned int &index)
{
	IPv4Interface iface;
	if (!selectInterface(iface) || iface.ip != ip)
	{
		errno = EADDRNOTAVAIL;
		return -1;
	}
	const int enabled = 1;
	if (setsockopt(fd, IPPROTO_IP, IP_PKTINFO, &enabled, sizeof(enabled)) < 0 ||
		setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &enabled, sizeof(enabled)) < 0)
		return -1;
	index = iface.index;
	return 0;
}

inline int sendDiscovery(int fd, const void *data, size_t length, const sockaddr_in &to,
	uint32_t ip, unsigned int index)
{
	iovec payload = {const_cast<void *>(data), length};
	alignas(cmsghdr) char control[CMSG_SPACE(sizeof(in_pktinfo))] = {};
	msghdr message = {};
	message.msg_name = const_cast<sockaddr_in *>(&to);
	message.msg_namelen = sizeof(to);
	message.msg_iov = &payload;
	message.msg_iovlen = 1;
	message.msg_control = control;
	message.msg_controllen = sizeof(control);
	cmsghdr *header = CMSG_FIRSTHDR(&message);
	header->cmsg_level = IPPROTO_IP;
	header->cmsg_type = IP_PKTINFO;
	header->cmsg_len = CMSG_LEN(sizeof(in_pktinfo));
	in_pktinfo *info = reinterpret_cast<in_pktinfo *>(CMSG_DATA(header));
	info->ipi_ifindex = static_cast<int>(index);
	info->ipi_spec_dst.s_addr = htonl(ip);
	// GeneralsX @bugfix Codex 04/10/2026 Log actual UDP transmission in release builds.
	const int result = static_cast<int>(sendmsg(fd, &message, 0));
	const int savedError = errno;
	char destination[INET_ADDRSTRLEN];
	inet_ntop(AF_INET, &to.sin_addr, destination, sizeof(destination));
	__android_log_print(result < 0 ? ANDROID_LOG_WARN : ANDROID_LOG_DEBUG, "GeneralsLAN",
		"UDP discovery send index=%u destination=%s:%u bytes=%d", index, destination, ntohs(to.sin_port), result);
	errno = savedError;
	return result;
}

inline int receiveDiscovery(int fd, void *data, size_t length, sockaddr_in *from, unsigned int index)
{
	// GeneralsX @bugfix Codex 04/10/2026 Drain discarded datagrams before returning to the lobby update.
	// Bound the work so traffic on another interface cannot stall the render thread.
	for (int attempts = 0; attempts < 64; ++attempts)
	{
		iovec payload = {data, length};
		alignas(cmsghdr) char control[CMSG_SPACE(sizeof(in_pktinfo))] = {};
		sockaddr_in sender = {};
		msghdr message = {};
		message.msg_name = &sender;
		message.msg_namelen = sizeof(sender);
		message.msg_iov = &payload;
		message.msg_iovlen = 1;
		message.msg_control = control;
		message.msg_controllen = sizeof(control);
		const int result = static_cast<int>(recvmsg(fd, &message, 0));
		if (result < 0)
			return result;
		if (!result || (message.msg_flags & (MSG_TRUNC | MSG_CTRUNC)))
			continue;
		for (cmsghdr *header = CMSG_FIRSTHDR(&message); header; header = CMSG_NXTHDR(&message, header))
		{
			if (header->cmsg_level == IPPROTO_IP && header->cmsg_type == IP_PKTINFO &&
				header->cmsg_len >= CMSG_LEN(sizeof(in_pktinfo)))
			{
				const in_pktinfo *info = reinterpret_cast<const in_pktinfo *>(CMSG_DATA(header));
				if (static_cast<unsigned int>(info->ipi_ifindex) != index)
					break;
				char source[INET_ADDRSTRLEN], destination[INET_ADDRSTRLEN];
				inet_ntop(AF_INET, &sender.sin_addr, source, sizeof(source));
				inet_ntop(AF_INET, &info->ipi_addr, destination, sizeof(destination));
				__android_log_print(ANDROID_LOG_DEBUG, "GeneralsLAN",
					"UDP discovery receive index=%u source=%s:%u destination=%s bytes=%d",
					index, source, ntohs(sender.sin_port), destination, result);
				if (from)
					*from = sender;
				return result;
			}
		}
	}
	return 0;
}
}
#endif
