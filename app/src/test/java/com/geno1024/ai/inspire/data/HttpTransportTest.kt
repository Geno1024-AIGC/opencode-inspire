package com.geno1024.ai.inspire.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress

class HttpTransportTest {

    @Test
    fun `http errors are thrown to the caller`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(502, -1)
            exchange.close()
        }
        server.start()
        try {
            val transport = HttpTransport("http://127.0.0.1:${server.address.port}")
            val error = runCatching { transport.executeRaw("GET", "/") }.exceptionOrNull()
            assertTrue(error is IOException)
            assertEquals(true, error?.message?.contains("HTTP 502"))
        } finally {
            server.stop(0)
        }
    }

    /**
     * The crash this guards against: an OkHttp callback resumed a *cancelled*
     * coroutine with an exception, which was then delivered as uncaught on
     * OkHttp's thread pool. Resuming with a value is a no-op when cancelled.
     */
    @Test
    fun `a cancelled callback never dispatches its error`() = runBlocking {
        val dispatched = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> dispatched += e }
        val job = launch(handler + Dispatchers.Unconfined) {
            val value = suspendCancellableCoroutine<Result<Int>> { cont ->
                cont.cancel(CancellationException("caller gone"))
                cont.resumeIfActive(Result.failure(IOException("HTTP 502: ")))
            }
            value.getOrThrow()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue("unexpected exceptions: $dispatched", dispatched.isEmpty())
    }

    @Test
    fun `corrupted gzip response is surfaced as IOException`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0, 0, 0, 0, 0, 0, 0x01, 0x02, 0x03)
            exchange.responseHeaders.add("Content-Encoding", "gzip")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val transport = HttpTransport("http://127.0.0.1:${server.address.port}")
            val error = runCatching { transport.executeRaw("GET", "/") }.exceptionOrNull()
            assertTrue("expected IOException, got $error", error is IOException)
        } finally {
            server.stop(0)
        }
    }
}
