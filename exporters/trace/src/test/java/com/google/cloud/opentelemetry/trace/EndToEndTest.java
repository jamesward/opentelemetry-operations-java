/*
 * Copyright 2021 Google
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.opentelemetry.trace;

import static org.junit.Assert.assertTrue;

import com.google.devtools.cloudtrace.v2.AttributeValue;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EndToEndTest {

  private static final String PROJECT_ID = "project-id";
  private static final Map<String, AttributeValue> FIXED_ATTRIBUTES = new HashMap<>();
  private static final String TRACE_ID = TraceId.fromLongs(321, 123);
  private static final String SPAN_ID = SpanId.fromLong(12345);
  private static final String PARENT_SPAN_ID = SpanId.fromLong(54321);
  private static final String SPAN_NAME = "MySpanName";
  private static final long START_EPOCH_NANOS = TimeUnit.SECONDS.toNanos(3000) + 200;
  private static final long END_EPOCH_NANOS = TimeUnit.SECONDS.toNanos(3001) + 255;
  private static final StatusData SPAN_DATA_STATUS = StatusData.ok();
  private static final String LOCALHOST = "127.0.0.1";

  private MockCloudTraceClient mockCloudTraceClient;
  private TraceExporter exporter;
  private Process mockServerProcess;

  @Before
  public void setup() throws MockServerStartupException {
    try {
      // Find a free port to spin up our server at.
      ServerSocket socket = new ServerSocket(0);
      int port = socket.getLocalPort();
      String address = String.format("%s:%d", LOCALHOST, port);
      socket.close();

      // Start the mock server. This assumes the binary is present and in $PATH.
      // Typically, the CI will be the one that curls the binary and adds it to $PATH.
      String[] cmdArray =
          new String[] {System.getProperty("mock.server.path"), "-address", address};
      ProcessBuilder pb = new ProcessBuilder(cmdArray);
      pb.redirectErrorStream(true);
      mockServerProcess = pb.start();

      // Setup the mock trace client.
      mockCloudTraceClient = new MockCloudTraceClient(LOCALHOST, port);

      // Block until the mock server starts (it will output the address after starting).
      BufferedReader br =
          new BufferedReader(new InputStreamReader(mockServerProcess.getInputStream()));
      br.readLine();
    } catch (Exception e) {
      StringBuilder error = new StringBuilder();
      error.append("Unable to start Google API Mock Server: ");
      error.append(System.getProperty("mock.server.path"));
      error.append("\n\tMake sure you're following the direction to run tests");
      error.append("\n\t$ source get_mock_server.sh");
      error.append("\n\t$ ./gradlew test -Dmock.server.path=$MOCKSERVER\n");
      throw new MockServerStartupException(error.toString(), e);
    }
  }

  @After
  public void tearDown() {
    mockServerProcess.destroy();
  }

  @Test
  public void exportMockSpanDataList() {
    exporter = new TraceExporter(PROJECT_ID, mockCloudTraceClient, FIXED_ATTRIBUTES);
    Collection<SpanData> spanDataList = new ArrayList<>();

    TestSpanData spanDataOne =
        TestSpanData.builder()
            .setParentSpanContext(
                SpanContext.createFromRemoteParent(
                    TRACE_ID, PARENT_SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()))
            .setSpanId(SPAN_ID)
            .setTraceId(TRACE_ID)
            .setName(SPAN_NAME)
            .setKind(Kind.SERVER)
            .setEvents(Collections.emptyList())
            .setStatus(SPAN_DATA_STATUS)
            .setStartEpochNanos(START_EPOCH_NANOS)
            .setEndEpochNanos(END_EPOCH_NANOS)
            .setTotalRecordedLinks(0)
            .setHasEnded(true)
            .build();

    spanDataList.add(spanDataOne);

    // Invokes export();
    assertTrue(exporter.export(spanDataList).isSuccess());
  }

  @Test
  public void exportEmptySpanDataList() {
    exporter = new TraceExporter(PROJECT_ID, mockCloudTraceClient, FIXED_ATTRIBUTES);
    Collection<SpanData> spanDataList = new ArrayList<>();

    // Invokes export();
    assertTrue(exporter.export(spanDataList).isSuccess());
  }
}
