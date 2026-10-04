/*
**	Command & Conquer Generals Zero Hour(tm)
**	Copyright 2025 Electronic Arts Inc.
**
**	This program is free software: you can redistribute it and/or modify
**	it under the terms of the GNU General Public License as published by
**	the Free Software Foundation, either version 3 of the License, or
**	(at your option) any later version.
**
**	This program is distributed in the hope that it will be useful,
**	but WITHOUT ANY WARRANTY; without even the implied warranty of
**	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**	GNU General Public License for more details.
**
**	You should have received a copy of the GNU General Public License
**	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

////////////////////////////////////////////////////////////////////////////////
//																																						//
//  (c) 2001-2003 Electronic Arts Inc.																				//
//																																						//
////////////////////////////////////////////////////////////////////////////////

// FILE: Udp.cpp //////////////////////////////////////////////////////////////
// Implementation of UDP socket wrapper class (taken from wnet lib)
// Author: Matthew D. Campbell, July 2001
///////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES ////////////////////////////////////////////////////////////
#include "PreRTS.h"	// This must go first in EVERY cpp file in the GameEngine

// USER INCLUDES //////////////////////////////////////////////////////////////
#include "Common/GameEngine.h"
//#include "GameNetwork/NetworkInterface.h"
#include "GameNetwork/udp.h"
#ifdef __ANDROID__
#include "Platform/AndroidLAN.h"
#endif


//-------------------------------------------------------------------------

#ifdef DEBUG_LOGGING

#define CASE(x) case (x): return #x;

AsciiString GetWSAErrorString( Int error )
{
	switch (error)
	{
		CASE(WSABASEERR)
		CASE(WSAEINTR)
		CASE(WSAEBADF)
		CASE(WSAEACCES)
		CASE(WSAEFAULT)
		CASE(WSAEINVAL)
		CASE(WSAEMFILE)
		CASE(WSAEWOULDBLOCK)
		CASE(WSAEINPROGRESS)
		CASE(WSAEALREADY)
		CASE(WSAENOTSOCK)
		CASE(WSAEDESTADDRREQ)
		CASE(WSAEMSGSIZE)
		CASE(WSAEPROTOTYPE)
		CASE(WSAENOPROTOOPT)
		CASE(WSAEPROTONOSUPPORT)
		CASE(WSAESOCKTNOSUPPORT)
		CASE(WSAEOPNOTSUPP)
		CASE(WSAEPFNOSUPPORT)
		CASE(WSAEAFNOSUPPORT)
		CASE(WSAEADDRINUSE)
		CASE(WSAEADDRNOTAVAIL)
		CASE(WSAENETDOWN)
		CASE(WSAENETUNREACH)
		CASE(WSAENETRESET)
		CASE(WSAECONNABORTED)
		CASE(WSAECONNRESET)
		CASE(WSAENOBUFS)
		CASE(WSAEISCONN)
		CASE(WSAENOTCONN)
		CASE(WSAESHUTDOWN)
		CASE(WSAETOOMANYREFS)
		CASE(WSAETIMEDOUT)
		CASE(WSAECONNREFUSED)
		CASE(WSAELOOP)
		CASE(WSAENAMETOOLONG)
		CASE(WSAEHOSTDOWN)
		CASE(WSAEHOSTUNREACH)
		CASE(WSAENOTEMPTY)
		CASE(WSAEPROCLIM)
		CASE(WSAEUSERS)
		CASE(WSAEDQUOT)
		CASE(WSAESTALE)
		CASE(WSAEREMOTE)
		CASE(WSAEDISCON)
		CASE(WSASYSNOTREADY)
		CASE(WSAVERNOTSUPPORTED)
		CASE(WSANOTINITIALISED)
		CASE(WSAHOST_NOT_FOUND)
		CASE(WSATRY_AGAIN)
		CASE(WSANO_RECOVERY)
		CASE(WSANO_DATA)
		default:
		{
			AsciiString ret;
			ret.format("Not a Winsock error (%d)", error);
			return ret;
		}
	}
	return AsciiString::TheEmptyString; // will not be hit, ever.
}

#undef CASE

#endif // defined(RTS_DEBUG)

//-------------------------------------------------------------------------

UDP::UDP()
{
  // GeneralsX @bugfix Codex 04/10/2026 Initialize descriptor, address and saved socket error state.
  fd=-1;
  myIP=0;
  myPort=0;
  m_lastError=0;
  m_lanInterfaceIndex=0;
  memset(&addr,0,sizeof(addr));
}

UDP::~UDP()
{
	if (fd != -1)
		closesocket(fd);
}

Int UDP::Bind(const char *Host,UnsignedShort port)
{
  struct hostent *hostStruct;
  struct in_addr *hostNode;

  if (isdigit(Host[0]))
    return ( Bind( ntohl(inet_addr(Host)), port) );

  hostStruct = gethostbyname(Host);
  if (hostStruct == nullptr)
    return (ADDRNOTAVAIL);
  hostNode = (struct in_addr *) hostStruct->h_addr;
  return ( Bind(ntohl(hostNode->s_addr),port) );
}

// You must call bind, implicit binding is for sissies
//   Well... you can get implicit binding if you pass 0 for either arg
Int UDP::Bind(UnsignedInt IP,UnsignedShort Port, Bool lanDiscovery)
{
  int retval;
  int status;

  // GeneralsX @bugfix Codex 04/10/2026 Close failed bind attempts and preserve host-order local endpoint.
  if (fd != -1)
    closesocket(fd);
  fd=-1;
  m_lanInterfaceIndex=0;
  ClearStatus();
  myIP=IP;
  myPort=Port;
  memset(&addr,0,sizeof(addr));

  addr.sin_family=AF_INET;
  addr.sin_port=htons(Port);
  addr.sin_addr.s_addr=htonl(IP);
  fd=socket(AF_INET,SOCK_DGRAM,DEFAULT_PROTOCOL);
  #ifdef _WIN32
  if (fd==SOCKET_ERROR)
    fd=-1;
  #endif
  if (fd==-1)
  {
    m_lastError=WSAGetLastError();
    return(UNKNOWN);
  }

#ifdef __ANDROID__
  // GeneralsX @bugfix Codex 04/10/2026 A unicast bind misses subnet broadcasts on Linux/Android.
  // IP_PKTINFO keeps outgoing traffic and incoming discovery on the chosen LAN, even when cellular is default.
  if (lanDiscovery)
  {
    if (GeneralsLAN::configureDiscoverySocket(fd, IP, m_lanInterfaceIndex) < 0)
    {
      m_lastError=errno;
      __android_log_print(ANDROID_LOG_ERROR, "GeneralsLAN", "Discovery socket setup failed: %s", strerror(m_lastError));
      closesocket(fd);
      fd=-1;
      return(UNKNOWN);
    }
    addr.sin_addr.s_addr=htonl(INADDR_ANY);
  }
#else
  (void)lanDiscovery;
#endif

  retval=bind(fd,(struct sockaddr *)&addr,sizeof(addr));

  #ifdef _WIN32
  if (retval==SOCKET_ERROR)
	{
    retval=-1;
		m_lastError = WSAGetLastError();
	}
  #endif
  if (retval==-1)
  {
    m_lastError=WSAGetLastError();
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "GeneralsLAN", "UDP bind failed IP=%08x port=%u: %s", IP, Port, strerror(m_lastError));
#endif
    status=GetStatus();
    closesocket(fd);
    fd=-1;
    return(status);
  }

  retval=SetBlocking(FALSE);
  if (retval != OK)
  {
    m_lastError=WSAGetLastError();
    closesocket(fd);
    fd=-1;
    return(UNKNOWN);
  }
#ifdef __ANDROID__
  if (lanDiscovery)
    __android_log_print(ANDROID_LOG_INFO, "GeneralsLAN", "Discovery bound to 0.0.0.0:%u, egress interface index=%u", Port, m_lanInterfaceIndex);
#endif

  return(OK);
}

Int UDP::getLocalAddr(UnsignedInt &ip, UnsignedShort &port)
{
  ip=myIP;
  port=myPort;
  return(OK);
}


// private function
Int UDP::SetBlocking(Int block)
{
  #ifdef _WIN32
   unsigned long flag=1;
   if (block)
     flag=0;
   int retval;
   retval=ioctlsocket(fd,FIONBIO,&flag);
   if (retval==SOCKET_ERROR)
     return(UNKNOWN);
   else
     return(OK);
  #else  // UNIX
   int flags = fcntl(fd, F_GETFL, 0);
   // GeneralsX @bugfix Codex 04/10/2026 Do not reuse a failed F_GETFL result as descriptor flags.
   if (flags < 0)
     return(UNKNOWN);
   if (block==FALSE)          // set nonblocking
     flags |= O_NONBLOCK;
   else                       // set blocking
     flags &= ~(O_NONBLOCK);

   if (fcntl(fd, F_SETFL, flags) < 0)
   {
     return(UNKNOWN);
   }
   return(OK);
  #endif
}


Int UDP::Write(const unsigned char *msg,UnsignedInt len,UnsignedInt IP,UnsignedShort port)
{
  Int retval;
  struct sockaddr_in to = {};

  // This happens frequently
  if ((IP==0)||(port==0)) return(ADDRNOTAVAIL);

#ifdef _UNIX
  errno=0;
#endif
  to.sin_port=htons(port);
  to.sin_addr.s_addr=htonl(IP);
  to.sin_family=AF_INET;

  ClearStatus();
#ifdef __ANDROID__
  // GeneralsX @bugfix Codex 04/10/2026 Pin broadcast and unicast discovery packets to the selected LAN source.
  if (m_lanInterfaceIndex)
    retval=GeneralsLAN::sendDiscovery(fd, msg, len, to, myIP, m_lanInterfaceIndex);
  else
#endif
    retval=sendto(fd,(const char *)msg,len,0,(struct sockaddr *)&to,sizeof(to));
#ifndef _WIN32
  if (retval < 0)
    m_lastError=errno;
#endif
#ifdef __ANDROID__
  if (retval < 0 && m_lanInterfaceIndex)
    __android_log_print(ANDROID_LOG_WARN, "GeneralsLAN", "UDP discovery send failed destination=%08x:%u: %s", IP, port, strerror(m_lastError));
#endif
  #ifdef _WIN32
  if (retval==SOCKET_ERROR)
	{
    retval=-1;
		m_lastError = WSAGetLastError();
#ifdef DEBUG_LOGGING
		static Int errCount = 0;
#endif
		DEBUG_ASSERTLOG(errCount++ > 100, ("UDP::Write() - WSA error is %s", GetWSAErrorString(WSAGetLastError()).str()));
	}
  #endif

  return(retval);
}

Int UDP::Read(unsigned char *msg,UnsignedInt len,sockaddr_in *from)
{
  Int retval;
  // GeneralsX @bugfix BenderAI 13/02/2026 Use socklen_t for POSIX socket functions (fighter19 pattern)
  socklen_t alen=sizeof(sockaddr_in);

#ifdef __ANDROID__
  // GeneralsX @bugfix Codex 04/10/2026 Accept unicast and subnet broadcasts only from the selected LAN interface.
  if (m_lanInterfaceIndex)
  {
    ClearStatus();
    retval=GeneralsLAN::receiveDiscovery(fd, msg, len, from, m_lanInterfaceIndex);
    if (retval < 0)
    {
      m_lastError=errno;
      if (m_lastError == EAGAIN || m_lastError == EWOULDBLOCK)
        return 0;
      __android_log_print(ANDROID_LOG_WARN, "GeneralsLAN", "UDP discovery receive failed: %s", strerror(m_lastError));
    }
    return retval;
  }
#endif

  if (from!=nullptr)
  {
    retval=recvfrom(fd,(char *)msg,len,0,(struct sockaddr *)from,&alen);
    #ifdef _WIN32
    if (retval == SOCKET_ERROR)
		{
			if (WSAGetLastError() != WSAEWOULDBLOCK)
			{
				// failing because of a blocking error isn't really such a bad thing.
				m_lastError = WSAGetLastError();
#ifdef DEBUG_LOGGING
				static Int errCount = 0;
#endif
				DEBUG_ASSERTLOG(errCount++ > 100, ("UDP::Read() - WSA error is %s", GetWSAErrorString(WSAGetLastError()).str()));
				retval = -1;
			} else {
				retval = 0;
			}
		}
    #endif
  }
  else
  {
    retval=recvfrom(fd,(char *)msg,len,0,nullptr,nullptr);
    #ifdef _WIN32
    if (retval==SOCKET_ERROR)
		{
			if (WSAGetLastError() != WSAEWOULDBLOCK)
			{
				// failing because of a blocking error isn't really such a bad thing.
				m_lastError = WSAGetLastError();
#ifdef DEBUG_LOGGING
				static Int errCount = 0;
#endif
				DEBUG_ASSERTLOG(errCount++ > 100, ("UDP::Read() - WSA error is %s", GetWSAErrorString(WSAGetLastError()).str()));
				retval = -1;
			} else {
				retval = 0;
			}
		}
    #endif
  }
  // GeneralsX @bugfix Codex 04/10/2026 POSIX would-block is an empty receive queue, not a transport failure.
#ifndef _WIN32
  if (retval < 0)
  {
    m_lastError=errno;
    if (m_lastError == EAGAIN || m_lastError == EWOULDBLOCK)
      return 0;
  }
#endif
  return(retval);
}


void UDP::ClearStatus()
{
  #ifndef _WIN32
  errno=0;
  #endif

	m_lastError = 0;
}

UDP::sockStat UDP::GetStatus()
{
	Int status = m_lastError;
 #ifdef _WIN32
  //int status=WSAGetLastError();
  switch (status) {
    case NO_ERROR:
      return OK;
    case WSAEINTR:
      return INTR;
    case WSAEINPROGRESS:
      return INPROGRESS;
    case WSAECONNREFUSED:
      return CONNREFUSED;
    case WSAEINVAL:
      return INVAL;
    case WSAEISCONN:
      return ISCONN;
    case WSAENOTSOCK:
      return NOTSOCK;
    case WSAETIMEDOUT:
      return TIMEDOUT;
    case WSAEALREADY:
      return ALREADY;
    case WSAEWOULDBLOCK:
      return WOULDBLOCK;
    case WSAEBADF:
      return BADF;
    default:
      return (UDP::sockStat)status;
  }
 #else
  //int status=errno;
  switch (status) {
    case 0:
      return OK;
    case EINTR:
      return INTR;
    case EINPROGRESS:
      return INPROGRESS;
    case ECONNREFUSED:
      return CONNREFUSED;
    case EINVAL:
      return INVAL;
    // GeneralsX @bugfix Codex 04/10/2026 Propagate unavailable LAN addresses and occupied lobby ports.
    case EADDRNOTAVAIL:
      return ADDRNOTAVAIL;
    case EADDRINUSE:
      return ADDRINUSE;
    case EISCONN:
      return ISCONN;
    case ENOTSOCK:
      return NOTSOCK;
    case ETIMEDOUT:
      return TIMEDOUT;
    case EALREADY:
      return ALREADY;
    case EAGAIN:
      return AGAIN;
    // GeneralsX @bugfix BenderAI 13/02/2026 EWOULDBLOCK == EAGAIN on Linux (duplicate case)
    #if EAGAIN != EWOULDBLOCK
    case EWOULDBLOCK:
      return WOULDBLOCK;
    #endif
    case EBADF:
      return BADF;
    default:
      return UNKNOWN;
  }
 #endif
}



/*
//
// Wait for net activity on this socket
//
int UDP::Wait(Int sec,Int usec,fd_set &returnSet)
{
  fd_set inputSet;

  FD_ZERO(&inputSet);
  FD_SET(fd,&inputSet);

  return(Wait(sec,usec,inputSet,returnSet));
}
*/

/*
//
// Wait for net activity on a list of sockets
//
int UDP::Wait(Int sec,Int usec,fd_set &givenSet,fd_set &returnSet)
{
  Wtime        timeout,timenow,timethen;
  fd_set       backupSet;
  int          retval=0,done,givenMax;
  Bool         noTimeout=FALSE;
  timeval      tv;

  returnSet=givenSet;
  backupSet=returnSet;

  if ((sec==-1)&&(usec==-1))
    noTimeout=TRUE;

  timeout.SetSec(sec);
  timeout.SetUsec(usec);
  timethen+=timeout;

  givenMax=fd;
  for (UnsignedInt i=0; i<(sizeof(fd_set)*8); i++)   // i=maxFD+1
  {
    if (FD_ISSET(i,&givenSet))
      givenMax=i;
  }
  ///DBGMSG("WAIT  fd="<<fd<<"  givenMax="<<givenMax);

  done=0;
  while( ! done)
  {
    if (noTimeout)
      retval=select(givenMax+1,&returnSet,0,0,nullptr);
    else
    {
      timeout.GetTimevalMT(tv);
      retval=select(givenMax+1,&returnSet,0,0,&tv);
    }

    if (retval>=0)
      done=1;

    else if ((retval==-1)&&(errno==EINTR))  // in case of signal
    {
      if (noTimeout==FALSE)
      {
        timenow.Update();
        timeout=timethen-timenow;
      }
      if ((noTimeout==FALSE)&&(timenow.GetSec()==0)&&(timenow.GetUsec()==0))
        done=1;
      else
        returnSet=backupSet;
    }
    else  // maybe out of memory?
    {
      done=1;
    }
  }
  ///DBGMSG("Wait retval: "<<retval);
  return(retval);
}
*/




// Set the kernel buffer sizes for incoming, and outgoing packets
//
// Linux seems to have a buffer max of 32767 bytes for this,
//  (which is the default). If you try and set the size to
//  greater than the default it just sets it to 32767.

Int UDP::SetInputBuffer(UnsignedInt bytes)
{
   int retval,arg=bytes;

   retval=setsockopt(fd,SOL_SOCKET,SO_RCVBUF,
     (char *)&arg,sizeof(int));
   if (retval==0)
     return(TRUE);
   else
     return(FALSE);
}

// Same note goes for the output buffer

Int UDP::SetOutputBuffer(UnsignedInt bytes)
{
   int retval,arg=bytes;

   retval=setsockopt(fd,SOL_SOCKET,SO_SNDBUF,
     (char *)&arg,sizeof(int));
   if (retval==0)
     return(TRUE);
   else
     return(FALSE);
}

// Get the system buffer sizes

int UDP::GetInputBuffer()
{
   int retval,arg=0;
   // GeneralsX @bugfix BenderAI 13/02/2026 Use socklen_t for POSIX socket functions (fighter19 pattern)
   socklen_t len=sizeof(int);

   retval=getsockopt(fd,SOL_SOCKET,SO_RCVBUF,
     (char *)&arg,&len);
   return(arg);
}


int UDP::GetOutputBuffer()
{
   int retval,arg=0;
   // GeneralsX @bugfix BenderAI 13/02/2026 Use socklen_t for POSIX socket functions (fighter19 pattern)
   socklen_t len=sizeof(int);

   retval=getsockopt(fd,SOL_SOCKET,SO_SNDBUF,
     (char *)&arg,&len);
   return(arg);
}

Int UDP::AllowBroadcasts(Bool status)
{
	int retval;
	BOOL val = status;
	retval = setsockopt(fd, SOL_SOCKET, SO_BROADCAST, (char *)&val, sizeof(BOOL));
	if (retval == 0)
		return TRUE;
	else
		return FALSE;
}
