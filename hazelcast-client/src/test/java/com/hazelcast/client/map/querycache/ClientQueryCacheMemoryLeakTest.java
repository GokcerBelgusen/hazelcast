/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.map.querycache;

import com.hazelcast.client.impl.querycache.ClientQueryCacheContext;
import com.hazelcast.client.proxy.ClientMapProxy;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.impl.MapService;
import com.hazelcast.map.impl.MapServiceContext;
import com.hazelcast.map.impl.querycache.QueryCacheContext;
import com.hazelcast.map.impl.querycache.accumulator.DefaultAccumulatorInfoSupplier;
import com.hazelcast.map.impl.querycache.publisher.MapListenerRegistry;
import com.hazelcast.map.impl.querycache.publisher.MapPublisherRegistry;
import com.hazelcast.map.impl.querycache.publisher.PartitionAccumulatorRegistry;
import com.hazelcast.map.impl.querycache.publisher.PublisherContext;
import com.hazelcast.map.impl.querycache.publisher.PublisherRegistry;
import com.hazelcast.map.impl.querycache.publisher.QueryCacheListenerRegistry;
import com.hazelcast.map.impl.querycache.subscriber.QueryCacheEndToEndProvider;
import com.hazelcast.map.impl.querycache.subscriber.QueryCacheFactory;
import com.hazelcast.map.impl.querycache.subscriber.SubscriberContext;
import com.hazelcast.query.TruePredicate;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.spi.impl.eventservice.impl.EventServiceImpl;
import com.hazelcast.spi.impl.eventservice.impl.EventServiceSegment;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class ClientQueryCacheMemoryLeakTest extends HazelcastTestSupport {

    private TestHazelcastFactory factory = new TestHazelcastFactory();

    @After
    public void tearDown() throws Exception {
        factory.shutdownAll();
    }

    @Test
    public void removes_internal_query_caches_upon_map_destroy() throws Exception {
        factory.newHazelcastInstance();
        HazelcastInstance client = factory.newHazelcastClient();

        String mapName = "test";
        IMap<Integer, Integer> map = client.getMap(mapName);

        populateMap(map);

        for (int j = 0; j < 10; j++) {
            map.getQueryCache(j + "-test-QC", TruePredicate.INSTANCE, true);
        }

        map.destroy();

        ClientQueryCacheContext queryCacheContext = ((ClientMapProxy) map).getQueryCacheContext();
        SubscriberContext subscriberContext = queryCacheContext.getSubscriberContext();
        QueryCacheEndToEndProvider provider = subscriberContext.getEndToEndQueryCacheProvider();
        QueryCacheFactory queryCacheFactory = subscriberContext.getQueryCacheFactory();

        assertEquals(0, provider.getQueryCacheCount(mapName));
        assertEquals(0, queryCacheFactory.getQueryCacheCount());
    }

    @Test
    public void no_query_cache_left_after_creating_and_destroying_same_map_concurrently() throws Exception {
        final HazelcastInstance node = factory.newHazelcastInstance();
        final HazelcastInstance client = factory.newHazelcastClient();
        final String mapName = "test";

        ExecutorService pool = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 1000; i++) {
            Runnable runnable = new Runnable() {
                public void run() {
                    IMap<Integer, Integer> map = client.getMap(mapName);
                    ;
                    try {
                        populateMap(map);
                        for (int j = 0; j < 10; j++) {
                            map.getQueryCache(j + "-test-QC", TruePredicate.INSTANCE, true);
                        }
                    } finally {
                        map.destroy();
                    }

                }
            };
            pool.submit(runnable);
        }

        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        SubscriberContext subscriberContext = getSubscriberContext(client, mapName);
        QueryCacheEndToEndProvider provider = subscriberContext.getEndToEndQueryCacheProvider();
        QueryCacheFactory queryCacheFactory = subscriberContext.getQueryCacheFactory();

        assertEquals(0, provider.getQueryCacheCount(mapName));
        assertEquals(0, queryCacheFactory.getQueryCacheCount());

        assertNoListenerLeftOnEventService(node);
        assertNoRegisteredListenerLeft(node, mapName);
        assertNoAccumulatorInfoSupplierLeft(node, mapName);
        assertNoPartitionAccumulatorRegistryLeft(node, mapName);
    }

    private static void assertNoAccumulatorInfoSupplierLeft(HazelcastInstance node, String mapName) {
        PublisherContext publisherContext = getPublisherContext(node);
        DefaultAccumulatorInfoSupplier accumulatorInfoSupplier
                = (DefaultAccumulatorInfoSupplier) publisherContext.getAccumulatorInfoSupplier();
        int accumulatorInfoCountOfMap = accumulatorInfoSupplier.accumulatorInfoCountOfMap(mapName);
        assertEquals(0, accumulatorInfoCountOfMap);
    }

    private static void assertNoRegisteredListenerLeft(HazelcastInstance node, String mapName) {
        PublisherContext publisherContext = getPublisherContext(node);
        MapListenerRegistry mapListenerRegistry = publisherContext.getMapListenerRegistry();
        QueryCacheListenerRegistry registry = mapListenerRegistry.getOrNull(mapName);
        if (registry != null) {
            Map<String, String> registeredListeners = registry.getAll();
            assertTrue(registeredListeners.isEmpty());
        }
    }

    private static void assertNoPartitionAccumulatorRegistryLeft(HazelcastInstance node, String mapName) {
        PublisherContext publisherContext = getPublisherContext(node);
        MapPublisherRegistry mapPublisherRegistry = publisherContext.getMapPublisherRegistry();
        PublisherRegistry registry = mapPublisherRegistry.getOrCreate(mapName);
        if(registry == null) {
            return;
        }

        Map<String, PartitionAccumulatorRegistry> accumulatorRegistryMap = registry.getAll();
        assertTrue(accumulatorRegistryMap.isEmpty());
    }

    private static void assertNoListenerLeftOnEventService(HazelcastInstance node) {
        NodeEngineImpl nodeEngineImpl = getNodeEngineImpl(node);
        EventServiceImpl eventService = ((EventServiceImpl) nodeEngineImpl.getEventService());
        EventServiceSegment segment = eventService.getSegment(MapService.SERVICE_NAME, false);
        ConcurrentMap registrationIdMap = segment.getRegistrationIdMap();
        assertEquals(registrationIdMap.toString(), 0, registrationIdMap.size());
    }

    private static void populateMap(IMap<Integer, Integer> map) {
        for (int i = 0; i < 10; i++) {
            map.put(i, i);
        }
    }

    private static SubscriberContext getSubscriberContext(HazelcastInstance client, String mapName) {
        final IMap<Integer, Integer> map = client.getMap(mapName);
        return ((ClientMapProxy) map).getQueryCacheContext().getSubscriberContext();
    }

    private static PublisherContext getPublisherContext(HazelcastInstance node) {
        MapService mapService = getNodeEngineImpl(node).getService(MapService.SERVICE_NAME);
        MapServiceContext mapServiceContext = mapService.getMapServiceContext();
        QueryCacheContext queryCacheContext = mapServiceContext.getQueryCacheContext();
        return queryCacheContext.getPublisherContext();
    }
}
