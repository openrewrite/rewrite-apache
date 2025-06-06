package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateApacheHttpCoreNioTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "httpclient", "httpcore", "httpmime", "httpcore-nio",
            "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @Test
    void nioSimplePackageChangesMigrated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
              import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;

              class A {
                  void method() {
                      ServerBootstrap bootstrap = ServerBootstrap.bootstrap();
                      DefaultHttpRequestParserFactory requestParserFactory = new DefaultHttpRequestParserFactory();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
              import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParserFactory;

              class A {
                  void method() {
                      ServerBootstrap bootstrap = ServerBootstrap.bootstrap();
                      DefaultHttpRequestParserFactory requestParserFactory = new DefaultHttpRequestParserFactory();
                  }
              }
              """
          )
        );
    }

    // TODO: DefaultListeningIOReactor, BasicNIOConnPool
    @Test
    void nioReactorImplementationImportsMigrated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
              import org.apache.http.impl.nio.reactor.IOReactorConfig;
              import org.apache.http.impl.nio.reactor.IOReactorConfig.Builder;
              import org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
              import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
              import org.apache.http.nio.reactor.IOEventDispatch;
              import org.apache.http.nio.reactor.IOReactor;

              class A {
                  void method(int bufferSize, IOEventDispatch eventDispatch) {
                      IOReactorConfig.Builder builder = new IOReactorConfig.Builder();
                      IOReactorConfig ioReactorConfig = builder.build();
                      SessionInputBufferImpl sessionInputBufferImpl = new SessionInputBufferImpl(bufferSize);
                      SessionOutputBufferImpl sessionOutputBufferImpl = new SessionOutputBufferImpl(bufferSize);
                      ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                      ioReactor.execute(eventDispatch);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.impl.nio.SessionInputBufferImpl;
              import org.apache.hc.core5.http.impl.nio.SessionOutputBufferImpl;
              import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
              import org.apache.hc.core5.reactor.IOEventHandlerFactory;
              import org.apache.hc.core5.reactor.IOReactor;
              import org.apache.hc.core5.reactor.IOReactorConfig;
              import org.apache.hc.core5.reactor.IOReactorConfig.Builder;

              class A {
                  void method(int bufferSize, IOEventHandlerFactory eventDispatch) {
                      IOReactorConfig.Builder builder = new IOReactorConfig.Builder();
                      IOReactorConfig ioReactorConfig = builder.build();
                      SessionInputBufferImpl sessionInputBufferImpl = new SessionInputBufferImpl(bufferSize);
                      SessionOutputBufferImpl sessionOutputBufferImpl = new SessionOutputBufferImpl(bufferSize);
                      IOReactor ioReactor = new DefaultConnectingIOReactor(eventDispatch);
                      ioReactor.execute();
                  }
              }
              """
          )
        );
    }

    @Test
    void nioUtilImportsMigrated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.nio.util.DirectByteBufferAllocator;
              import org.apache.http.nio.util.ExpandableBuffer;
              import org.apache.http.nio.util.HeapByteBufferAllocator;
              import org.apache.http.nio.util.SharedInputBuffer;
              import org.apache.http.nio.util.SharedOutputBuffer;

              import java.nio.ByteBuffer;

              class A {
                  void method(int bufferSize) {
                      ByteBuffer directBuffer = new DirectByteBufferAllocator().allocate(bufferSize);
                      ByteBuffer heapBuffer = new HeapByteBufferAllocator().allocate(bufferSize);
                      ExpandableBuffer b = new ExpandableBuffer(bufferSize, new HeapByteBufferAllocator());
                      SharedInputBuffer sib = new SharedInputBuffer(bufferSize, new DirectByteBufferAllocator());
                      SharedOutputBuffer sob = new SharedOutputBuffer(bufferSize, new DirectByteBufferAllocator());
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.impl.nio.ExpandableBuffer;
              import org.apache.hc.core5.http.nio.support.classic.SharedInputBuffer;
              import org.apache.hc.core5.http.nio.support.classic.SharedOutputBuffer;

              import java.nio.ByteBuffer;

              class A {
                  void method(int bufferSize) {
                      ByteBuffer directBuffer = ByteBuffer.allocateDirect(bufferSize);
                      ByteBuffer heapBuffer = ByteBuffer.allocate(bufferSize);
                      ExpandableBuffer b = new ExpandableBuffer(bufferSize);
                      SharedInputBuffer sib = new SharedInputBuffer(bufferSize);
                      SharedOutputBuffer sob = new SharedOutputBuffer(bufferSize);
                  }
              }
              """
          )
        );
    }
}
