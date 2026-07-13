/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateApacheHttpCoreNioTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpasyncclient-4", "httpcore-nio-4",
            "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpCore_5_NioClassMapping");
    }

    @DocumentExample
    @Test
    void migratesIOReactorConfig() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.reactor.IOReactorConfig;
              import org.apache.http.impl.nio.reactor.IOReactorConfig.Builder;

              class A {
                  void method() {
                      IOReactorConfig.Builder builder = new IOReactorConfig.Builder();
                      builder.setSelectInterval(500);
                      builder.setShutdownGracePeriod(400);
                      builder.setInterestOpQueued(true);
                      builder.setSoTimeout(300);
                      builder.setSoLinger(200);
                      builder.setConnectTimeout(100);
                      IOReactorConfig ioReactorConfig = builder.build();
                      long selectInterval = ioReactorConfig.getSelectInterval();
                      ioReactorConfig.getShutdownGracePeriod();
                      ioReactorConfig.isInterestOpQueued();
                      int soTimeout = ioReactorConfig.getSoTimeout();
                      int soLinger = ioReactorConfig.getSoLinger();
                      ioReactorConfig.getConnectTimeout();
                  }
              }
              """,
            """
              import org.apache.hc.core5.reactor.IOReactorConfig;
              import org.apache.hc.core5.reactor.IOReactorConfig.Builder;
              import org.apache.hc.core5.util.TimeValue;
              import org.apache.hc.core5.util.Timeout;

              import java.util.concurrent.TimeUnit;

              class A {
                  void method() {
                      IOReactorConfig.Builder builder = new IOReactorConfig.Builder();
                      builder.setSelectInterval(TimeValue.of(500, TimeUnit.MILLISECONDS));
                      builder.setSoTimeout(300, TimeUnit.MILLISECONDS);
                      builder.setSoLinger(200, TimeUnit.MILLISECONDS);
                      IOReactorConfig ioReactorConfig = builder.build();
                      TimeValue selectInterval = ioReactorConfig.getSelectInterval();
                      Timeout soTimeout = ioReactorConfig.getSoTimeout();
                      TimeValue soLinger = ioReactorConfig.getSoLinger();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateDependencies() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.httpcomponents</groupId>
                          <artifactId>httpcore-nio</artifactId>
                          <version>4.4.16</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("5\\.3\\.\\d+").matcher(pom);
                assertThat(version.find()).describedAs("Expected 5.3.x in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.httpcomponents.core5</groupId>
                              <artifactId>httpcore5</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version.group(0));
            })
          )
        );
    }

    @Test
    void migratesSharedInputBuffer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.nio.util.DirectByteBufferAllocator;
              import org.apache.http.nio.util.SharedInputBuffer;

              class A {
                  void method() {
                      SharedInputBuffer inBuffer1 = new SharedInputBuffer(1);
                      SharedInputBuffer inBuffer2 = new SharedInputBuffer(2, DirectByteBufferAllocator.INSTANCE);
                      inBuffer1.capacity();
                      int available1 = inBuffer1.available();
                      byte[] bArr = "testing".getBytes();
                      int readCount = inBuffer2.read(bArr);
                      int readCount2 = inBuffer2.read("testing2".getBytes());
                      inBuffer2.close();
                      inBuffer2.shutdown();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.support.classic.SharedInputBuffer;

              class A {
                  void method() {
                      SharedInputBuffer inBuffer1 = new SharedInputBuffer(1);
                      SharedInputBuffer inBuffer2 = new SharedInputBuffer(2);
                      /* TODO: Check this usage, as implementation has changed to match that of old `.available()` method. */
                      inBuffer1.capacity();
                      int available1 = inBuffer1.capacity();
                      byte[] bArr = "testing".getBytes();
                      int readCount = inBuffer2.read(bArr, 0, bArr.length);
                      int readCount2 = /* TODO: Please check that repeated obtaining of byte[] is safe here */ inBuffer2.read("testing2".getBytes(), 0, "testing2".getBytes().length);
                      inBuffer2.markEndStream();
                      inBuffer2.abort();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesSharedOutputBuffer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.nio.util.HeapByteBufferAllocator;
              import org.apache.http.nio.util.SharedOutputBuffer;

              class A {
                  void method() {
                      SharedOutputBuffer outBuffer1 = new SharedOutputBuffer(3);
                      SharedOutputBuffer outBuffer2 = new SharedOutputBuffer(4, HeapByteBufferAllocator.INSTANCE);
                      outBuffer1.capacity();
                      int available1 = outBuffer1.available();
                      byte[] bArr = "testing".getBytes();
                      outBuffer1.write(bArr);
                      outBuffer2.write("testing2".getBytes());
                      outBuffer2.close();
                      outBuffer2.shutdown();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.support.classic.SharedOutputBuffer;

              class A {
                  void method() {
                      SharedOutputBuffer outBuffer1 = new SharedOutputBuffer(3);
                      SharedOutputBuffer outBuffer2 = new SharedOutputBuffer(4);
                      /* TODO: Check this usage, as implementation has changed to match that of old `.available()` method. */
                      outBuffer1.capacity();
                      int available1 = outBuffer1.capacity();
                      byte[] bArr = "testing".getBytes();
                      outBuffer1.write(bArr, 0, bArr.length);
                      /* TODO: Please check that repeated obtaining of byte[] is safe here */
                      outBuffer2.write("testing2".getBytes(), 0, "testing2".getBytes().length);
                      outBuffer2.abort();
                      outBuffer2.abort();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesUtilClasses() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import java.nio.ByteBuffer;

              public class BufferWrapper extends ByteBuffer {}
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              import com.example.BufferWrapper;
              import org.apache.http.nio.util.ContentInputBuffer;
              import org.apache.http.nio.util.ContentOutputBuffer;
              import org.apache.http.nio.util.DirectByteBufferAllocator;
              import org.apache.http.nio.util.HeapByteBufferAllocator;
              import org.apache.http.nio.util.SharedInputBuffer;
              import org.apache.http.nio.util.SharedOutputBuffer;
              import org.apache.http.nio.util.SimpleInputBuffer;
              import org.apache.http.nio.util.SimpleOutputBuffer;

              class A {
                  void method() {
                      ContentInputBuffer inBuffer1 = new SharedInputBuffer(1);
                      ContentOutputBuffer outBuffer1 = new SharedOutputBuffer(2);
                      BufferWrapper bb1 = (BufferWrapper) new DirectByteBufferAllocator().allocate(3);
                      BufferWrapper bb2 = (BufferWrapper) DirectByteBufferAllocator.INSTANCE.allocate(4);
                      BufferWrapper bb3 = (BufferWrapper) new HeapByteBufferAllocator().allocate(5);
                      BufferWrapper bb4 = (BufferWrapper) HeapByteBufferAllocator.INSTANCE.allocate(6);
                      SimpleInputBuffer sib = new SimpleInputBuffer(7);
                      SimpleOutputBuffer sob = new SimpleOutputBuffer(8);
                  }
              }
              """,
            """
              import com.example.BufferWrapper;
              import org.apache.hc.core5.http.nio.support.classic.ContentInputBuffer;
              import org.apache.hc.core5.http.nio.support.classic.ContentOutputBuffer;
              import org.apache.hc.core5.http.nio.support.classic.SharedInputBuffer;
              import org.apache.hc.core5.http.nio.support.classic.SharedOutputBuffer;
              import org.apache.http.nio.util.SimpleInputBuffer;
              import org.apache.http.nio.util.SimpleOutputBuffer;

              import java.nio.ByteBuffer;

              class A {
                  void method() {
                      ContentInputBuffer inBuffer1 = new SharedInputBuffer(1);
                      ContentOutputBuffer outBuffer1 = new SharedOutputBuffer(2);
                      BufferWrapper bb1 = (BufferWrapper) ByteBuffer.allocateDirect(3);
                      BufferWrapper bb2 = (BufferWrapper) ByteBuffer.allocateDirect(4);
                      BufferWrapper bb3 = (BufferWrapper) ByteBuffer.allocate(5);
                      BufferWrapper bb4 = (BufferWrapper) ByteBuffer.allocate(6);
                      SimpleInputBuffer sib = /* TODO: Please remove usages of `SimpleInputBuffer`, as no direct migration exists */ new SimpleInputBuffer(7);
                      SimpleOutputBuffer sob = /* TODO: Please remove usages of `SimpleOutputBuffer`, as no direct migration exists */ new SimpleOutputBuffer(8);
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesIOReactorClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  ConnectingIOReactor getReactor() throws Exception {
                      return new DefaultConnectingIOReactor();
                  }
              }
              """,
            """
              import org.apache.hc.core5.reactor.ConnectionInitiator;
              import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;

              class A {
                  ConnectionInitiator getReactor() throws Exception {
                      return new DefaultConnectingIOReactor();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape1NStringEntityToStringEntity() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NStringEntity;

              class A {
                  void method(HttpPost post, String json) {
                      post.setEntity(new NStringEntity(json, ContentType.APPLICATION_JSON));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.io.entity.StringEntity;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;

              class A {
                  void method(HttpPost post, String json) {
                      post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape1NByteArrayEntityToByteArrayEntity() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NByteArrayEntity;

              class A {
                  void method(HttpPost post, byte[] bytes) {
                      post.setEntity(new NByteArrayEntity(bytes, ContentType.APPLICATION_OCTET_STREAM));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;

              class A {
                  void method(HttpPost post, byte[] bytes) {
                      post.setEntity(new ByteArrayEntity(bytes, ContentType.APPLICATION_OCTET_STREAM));
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape1NFileEntityToFileEntity() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NFileEntity;

              import java.io.File;

              class A {
                  void method(HttpPost post, File file) {
                      post.setEntity(new NFileEntity(file, ContentType.APPLICATION_OCTET_STREAM));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.io.entity.FileEntity;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;

              import java.io.File;

              class A {
                  void method(HttpPost post, File file) {
                      post.setEntity(new FileEntity(file, ContentType.APPLICATION_OCTET_STREAM));
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape2BasicAsyncRequestProducerWithNStringEntity() {
        rewriteRun(
          // Before-image: `setEntity(...)` returns void in 4.x so the inlined `new BasicAsyncRequestProducer(...)` constructor cannot be typed.
          // After-image: the migrated `build()` returns a core5 producer the un-migrated 4.x `BasicAsyncRequestProducer` return type cannot accept.
          spec -> spec.typeValidationOptions(TypeValidation.all().constructorInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=java
          java(
            """
              import org.apache.http.HttpHost;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NStringEntity;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      return new BasicAsyncRequestProducer(
                          HttpHost.create("http://example.com"),
                          new HttpPost("/api").setEntity(new NStringEntity(body, ContentType.APPLICATION_JSON)));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
              import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      return AsyncRequestBuilder.post("http://example.com" + "/api").setEntity(AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON)).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape2WithHoistedRequest() {
        rewriteRun(
          // Only the after-image needs relaxing: the migrated `AsyncRequestBuilder...build()` returns a core5 producer, but this recipe intentionally leaves the enclosing method's 4.x `BasicAsyncRequestProducer` return type for a later step, so the `build()` invocation cannot be type-attributed.
          spec -> spec.afterTypeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=java
          java(
            """
              import org.apache.http.HttpHost;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NStringEntity;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      HttpPost post = new HttpPost("/api");
                      post.setEntity(new NStringEntity(body, ContentType.APPLICATION_JSON));
                      return new BasicAsyncRequestProducer(HttpHost.create("http://example.com"), post);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
              import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      return AsyncRequestBuilder.post("http://example.com" + "/api").setEntity(AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON)).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape2WithHoistedNStringEntity() {
        rewriteRun(
          // Before-image: `setEntity(...)` returns void in 4.x so the inlined `new BasicAsyncRequestProducer(...)` constructor cannot be typed.
          // After-image: the migrated `build()` returns a core5 producer the un-migrated 4.x `BasicAsyncRequestProducer` return type cannot accept.
          spec -> spec.typeValidationOptions(TypeValidation.all().constructorInvocations(false))
            .afterTypeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=java
          java(
            """
              import org.apache.http.HttpHost;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NStringEntity;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      NStringEntity entity = new NStringEntity(body, ContentType.APPLICATION_JSON);
                      return new BasicAsyncRequestProducer(
                          HttpHost.create("http://example.com"),
                          new HttpPost("/api").setEntity(entity));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
              import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      return AsyncRequestBuilder.post("http://example.com" + "/api").setEntity(AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON)).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape2WithHoistedRequestAndEntity() {
        rewriteRun(
          // Only the after-image needs relaxing: the migrated `AsyncRequestBuilder...build()` returns a core5 producer, but this recipe intentionally leaves the enclosing method's 4.x `BasicAsyncRequestProducer` return type for a later step, so the `build()` invocation cannot be type-attributed.
          spec -> spec.afterTypeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=java
          java(
            """
              import org.apache.http.HttpHost;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NStringEntity;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      NStringEntity entity = new NStringEntity(body, ContentType.APPLICATION_JSON);
                      HttpPost post = new HttpPost("/api");
                      post.setEntity(entity);
                      return new BasicAsyncRequestProducer(HttpHost.create("http://example.com"), post);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
              import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      return AsyncRequestBuilder.post("http://example.com" + "/api").setEntity(AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON)).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotInlineMultiUseLocal() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpHost;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.entity.NStringEntity;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      HttpPost post = new HttpPost("/api");
                      post.setEntity(new NStringEntity(body, ContentType.APPLICATION_JSON));
                      post.addHeader("X-Extra", "1");
                      return new BasicAsyncRequestProducer(HttpHost.create("http://example.com"), post);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.io.entity.StringEntity;
              import org.apache.http.HttpHost;
              import org.apache.http.client.methods.HttpPost;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.protocol.BasicAsyncRequestProducer;

              class A {
                  BasicAsyncRequestProducer producer(String body) {
                      HttpPost post = new HttpPost("/api");
                      post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                      post.addHeader("X-Extra", "1");
                      return new BasicAsyncRequestProducer(HttpHost.create("http://example.com"), post);
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape3HttpAsyncMethodsCreatePost() {
        rewriteRun(
          // Only the after-image needs relaxing: the migrated `AsyncRequestBuilder...build()` returns a core5 producer, but this recipe intentionally leaves the enclosing method's 4.x `BasicAsyncRequestProducer` return type for a later step, so the `build()` invocation cannot be type-attributed.
          spec -> spec.afterTypeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.client.methods.HttpAsyncMethods;
              import org.apache.http.nio.protocol.HttpAsyncRequestProducer;

              import java.net.URI;

              class A {
                  HttpAsyncRequestProducer producer(URI uri, String body) throws Exception {
                      return HttpAsyncMethods.createPost(uri, body, ContentType.APPLICATION_JSON);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
              import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
              import org.apache.http.entity.ContentType;
              import org.apache.http.nio.protocol.HttpAsyncRequestProducer;

              import java.net.URI;

              class A {
                  HttpAsyncRequestProducer producer(URI uri, String body) throws Exception {
                      return AsyncRequestBuilder.post(uri).setEntity(AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON)).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape3HttpAsyncMethodsCreateGet() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.nio.client.methods.HttpAsyncMethods;
              import org.apache.http.nio.protocol.HttpAsyncRequestProducer;

              import java.net.URI;

              class A {
                  HttpAsyncRequestProducer producer(URI uri) {
                      return HttpAsyncMethods.createGet(uri);
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
              import org.apache.http.nio.protocol.HttpAsyncRequestProducer;

              import java.net.URI;

              class A {
                  HttpAsyncRequestProducer producer(URI uri) {
                      return AsyncRequestBuilder.get(uri).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesShape3HttpAsyncMethodsCreateConsumer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.HttpResponse;
              import org.apache.http.nio.client.methods.HttpAsyncMethods;
              import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;

              class A {
                  HttpAsyncResponseConsumer<HttpResponse> consumer() {
                      return HttpAsyncMethods.createConsumer();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
              import org.apache.http.HttpResponse;
              import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;

              class A {
                  HttpAsyncResponseConsumer<HttpResponse> consumer() {
                      return SimpleResponseConsumer.create();
                  }
              }
              """
          )
        );
    }
}
