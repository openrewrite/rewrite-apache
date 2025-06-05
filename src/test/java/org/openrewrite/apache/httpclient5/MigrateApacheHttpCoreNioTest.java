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

    /*

    //              import org.apache.http.nio.util.BufferInfo;
              import org.apache.http.nio.util.ByteBufferAllocator;
//              import org.apache.http.nio.util.ContentInputBuffer;
//              import org.apache.http.nio.util.ContentOutputBuffer;
//              import org.apache.http.nio.util.DirectByteBufferAllocator;
              import org.apache.http.nio.util.ExpandableBuffer;
//              import org.apache.http.nio.util.HeapByteBufferAllocator;
//              import org.apache.http.nio.util.SharedInputBuffer;
//              import org.apache.http.nio.util.SharedOutputBuffer;
//              import org.apache.http.nio.util.SimpleInputBuffer;
//              import org.apache.http.nio.util.SimpleOutputBuffer;
     */
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
