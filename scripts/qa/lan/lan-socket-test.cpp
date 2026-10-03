// GeneralsX @bugfix Codex 04/10/2026 Exercise IP_PKTINFO send/receive and discarded datagram draining.
#include "Platform/AndroidLAN.h"
#include <cassert>
#include <fcntl.h>
#include <cstdio>

int main()
{
	const unsigned int loopback = if_nametoindex("lo");
	assert(loopback);
	const int receiver = socket(AF_INET, SOCK_DGRAM, 0);
	const int sender = socket(AF_INET, SOCK_DGRAM, 0);
	assert(receiver >= 0 && sender >= 0);
	const int enabled = 1;
	assert(setsockopt(receiver, IPPROTO_IP, IP_PKTINFO, &enabled, sizeof(enabled)) == 0);
	assert(fcntl(receiver, F_SETFL, O_NONBLOCK) == 0);
	sockaddr_in endpoint = {};
	endpoint.sin_family = AF_INET;
	endpoint.sin_addr.s_addr = htonl(INADDR_ANY);
	assert(bind(receiver, reinterpret_cast<sockaddr *>(&endpoint), sizeof(endpoint)) == 0);
	socklen_t size = sizeof(endpoint);
	assert(getsockname(receiver, reinterpret_cast<sockaddr *>(&endpoint), &size) == 0);
	endpoint.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	const char oversized[256] = {};
	const char payload[] = "LAN discovery";
	assert(GeneralsLAN::sendDiscovery(sender, oversized, sizeof(oversized), endpoint, INADDR_LOOPBACK, loopback) == static_cast<int>(sizeof(oversized)));
	assert(GeneralsLAN::sendDiscovery(sender, payload, sizeof(payload), endpoint, INADDR_LOOPBACK, loopback) == static_cast<int>(sizeof(payload)));
	char received[64] = {};
	sockaddr_in from = {};
	// The truncated first packet must not hide the next valid packet until a later frame.
	assert(GeneralsLAN::receiveDiscovery(receiver, received, sizeof(received), &from, loopback) == static_cast<int>(sizeof(payload)));
	assert(std::strcmp(received, payload) == 0);
	assert(ntohl(from.sin_addr.s_addr) == INADDR_LOOPBACK);
	assert(GeneralsLAN::receiveDiscovery(receiver, received, sizeof(received), &from, loopback) == -1);
	assert(errno == EAGAIN || errno == EWOULDBLOCK);
	assert(GeneralsLAN::sendDiscovery(sender, payload, sizeof(payload), endpoint, INADDR_LOOPBACK, loopback) == static_cast<int>(sizeof(payload)));
	// A packet delivered on a different interface is consumed but never exposed to LANAPI.
	assert(GeneralsLAN::receiveDiscovery(receiver, received, sizeof(received), &from, loopback + 1000) == -1);
	assert(errno == EAGAIN || errno == EWOULDBLOCK);
	close(receiver);
	close(sender);
	std::puts("LAN socket helpers: all cases passed");
}
