package java.net

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_UNSPEC
import platform.posix.EAGAIN
import platform.posix.EINPROGRESS
import platform.posix.EINTR
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.IPPROTO_TCP
import platform.posix.O_NONBLOCK
import platform.posix.POLLOUT
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.SO_LINGER
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_SNDTIMEO
import platform.posix.TCP_NODELAY
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getsockopt
import platform.posix.linger
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.strerror
import platform.posix.timeval

/*
 *  shimِ iOS ِ `java.net.Socket` روی سوکتِ POSIX ِ خودِ iOS — همان «چاپگرِ شبکه، درگاهِ ۹۱۰۰» ِ اندروید.
 *  همان قراردادِ JDK: مهلتِ وصل ⇐ SocketTimeoutException، ردِ وصل ⇐ ConnectException، نامِ ناپیدا ⇐
 *  UnknownHostException، مهلتِ خواندن (`soTimeout`) ⇐ SocketTimeoutException. SIGPIPE خاموش است (SO_NOSIGPIPE).
 */
class InetSocketAddress(val hostString: String, val port: Int) {
  val hostName: String get() = hostString
  override fun toString(): String = "$hostString:$port"
}

open class Socket() : java.io.Closeable {
  private var fd = -1
  private var timeoutMs = 0

  constructor(host: String, port: Int) : this() { connect(InetSocketAddress(host, port), 0) }

  val isConnected: Boolean get() = fd >= 0
  val isClosed: Boolean get() = closed
  private var closed = false

  var soTimeout: Int
    get() = timeoutMs
    set(v) { timeoutMs = v; if (fd >= 0) applyTimeouts() }

  var tcpNoDelay: Boolean = false
    set(v) { field = v; if (fd >= 0) setInt(IPPROTO_TCP, TCP_NODELAY, if (v) 1 else 0) }

  fun setSoLinger(on: Boolean, linger: Int) {
    if (fd < 0) return
    memScoped {
      val l = alloc<linger>()
      l.l_onoff = if (on) 1 else 0
      l.l_linger = linger
      setsockopt(fd, SOL_SOCKET, SO_LINGER, l.ptr, sizeOf<linger>().convert())
    }
  }

  fun connect(endpoint: InetSocketAddress, timeout: Int = 0) {
    memScoped {
      val hints = alloc<addrinfo>()
      hints.ai_family = AF_UNSPEC
      hints.ai_socktype = SOCK_STREAM
      val res = alloc<CPointerVar<addrinfo>>()
      if (getaddrinfo(endpoint.hostString, endpoint.port.toString(), hints.ptr, res.ptr) != 0 || res.value == null) {
        throw UnknownHostException(endpoint.hostString)
      }
      var last: IOException = ConnectException("Connection refused")
      try {
        var ai = res.value
        while (ai != null) {
          val a = ai.pointed
          val s = socket(a.ai_family, a.ai_socktype, a.ai_protocol)
          if (s >= 0) {
            try {
              connectOne(s, a, timeout)
              fd = s
              setInt(SOL_SOCKET, SO_NOSIGPIPE, 1)
              applyTimeouts()
              return
            } catch (e: IOException) {
              close(s)
              last = e
            }
          }
          ai = a.ai_next
        }
      } finally {
        freeaddrinfo(res.value)
      }
      throw last
    }
  }

  private fun connectOne(s: Int, a: addrinfo, timeout: Int) = memScoped {
    val flags = fcntl(s, F_GETFL, 0)
    fcntl(s, F_SETFL, flags or O_NONBLOCK)
    if (connect(s, a.ai_addr, a.ai_addrlen) != 0) {
      if (errno != EINPROGRESS) throw ConnectException("Connection refused: ${strerror(errno)?.toKString()}")
      val p = alloc<pollfd>()
      p.fd = s
      p.events = POLLOUT.convert()
      val n = poll(p.ptr, 1u, if (timeout > 0) timeout else -1)
      if (n == 0) throw SocketTimeoutException("connect timed out")
      if (n < 0) throw ConnectException(strerror(errno)?.toKString())
      val err = alloc<IntVar>()
      val len = alloc<platform.posix.socklen_tVar>()
      len.value = sizeOf<IntVar>().convert()
      getsockopt(s, SOL_SOCKET, SO_ERROR, err.ptr, len.ptr)
      if (err.value != 0) throw ConnectException("Connection refused: ${strerror(err.value)?.toKString()}")
    }
    fcntl(s, F_SETFL, flags)
  }

  private fun setInt(level: Int, name: Int, v: Int) = memScoped {
    val x = alloc<IntVar>()
    x.value = v
    setsockopt(fd, level, name, x.ptr, sizeOf<IntVar>().convert())
  }

  private fun applyTimeouts() = memScoped {
    val tv = alloc<timeval>()
    tv.tv_sec = (timeoutMs / 1000).convert()
    tv.tv_usec = ((timeoutMs % 1000) * 1000).convert()
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, tv.ptr, sizeOf<timeval>().convert())
  }

  val outputStream: OutputStream get() = getOutputStream()
  val inputStream: InputStream get() = getInputStream()

  fun getOutputStream(): OutputStream {
    if (fd < 0) throw SocketException("Socket is not connected")
    return object : OutputStream() {
      override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
      override fun write(b: ByteArray, off: Int, len: Int) {
        var done = 0
        while (done < len) {
          val n = b.usePinned { send(fd, it.addressOf(off + done), (len - done).convert(), 0) }
          if (n < 0) {
            if (errno == EINTR) continue
            if (errno == EAGAIN) throw SocketTimeoutException("Write timed out")
            throw SocketException("Broken pipe: ${strerror(errno)?.toKString()}")
          }
          done += n.toInt()
        }
      }
    }
  }

  fun getInputStream(): InputStream {
    if (fd < 0) throw SocketException("Socket is not connected")
    return object : InputStream() {
      override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
      }

      override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
          val n = b.usePinned { recv(fd, it.addressOf(off), len.convert(), 0) }
          if (n > 0) return n.toInt()
          if (n == 0L) return -1
          if (errno == EINTR) continue
          if (errno == EAGAIN) throw SocketTimeoutException("Read timed out")
          throw SocketException(strerror(errno)?.toKString())
        }
      }
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    if (fd >= 0) close(fd)
    fd = -1
  }
}
