/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.async;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SynchronizationAdapter;

/**
 * @version 
 */
public class AsyncEndpointUoWFailedTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;
    private MySynchronization sync = new MySynchronization();

    public void testAsyncEndpoint() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.requestBody("direct:start", "Hello Camel", String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Damn", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        // wait a bit to ensure UoW has been run
        Thread.sleep(1000);

        assertFalse("Should use different threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
        assertEquals(0, sync.isOnComplete());
        assertEquals(1, sync.isOnFailure());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                beforeThreadName = Thread.currentThread().getName();
                                exchange.addOnCompletion(sync);
                            }
                        })
                        .to("mock:before")
                        .to("log:before")
                        .to("async:Bye Camel")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("log:after")
                        .to("mock:after")
                        .throwException(new IllegalArgumentException("Damn"))
                        .to("mock:result");
            }
        };
    }

    private class MySynchronization extends SynchronizationAdapter {

        private AtomicInteger onComplete = new AtomicInteger();
        private AtomicInteger onFailure = new AtomicInteger();

        public void onComplete(Exchange exchange) {
            onComplete.incrementAndGet();
        }

        @Override
        public void onFailure(Exchange exchange) {
            onFailure.incrementAndGet();
        }

        public int isOnComplete() {
            return onComplete.get();
        }

        public int isOnFailure() {
            return onFailure.get();
        }

    }

}