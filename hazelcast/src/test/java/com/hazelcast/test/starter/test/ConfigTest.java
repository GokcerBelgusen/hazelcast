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

package com.hazelcast.test.starter.test;

import com.hazelcast.config.Config;
import com.hazelcast.config.ListConfig;
import com.hazelcast.config.ListenerConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.test.starter.ConfigConstructor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;

import static com.hazelcast.test.HazelcastTestSupport.assertPropertiesEquals;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class ConfigTest {

    @Test
    public void configCloneTest() throws Exception {
        Config thisConfig = new Config();
        thisConfig.setInstanceName("TheAssignedName");
        thisConfig.addMapConfig(new MapConfig("myMap"));

        thisConfig.addListConfig(new ListConfig("myList"));

        thisConfig.addListenerConfig(new ListenerConfig("the.listener.config.class"));

        thisConfig.setProperties(buildPropertiesWithDefaults());

        ConfigConstructor configConstructor = new ConfigConstructor(Config.class);

        Config otherConfig = (Config) configConstructor.createNew(thisConfig);
        assertEquals(otherConfig.getInstanceName(), thisConfig.getInstanceName());
        assertEquals(otherConfig.getMapConfigs().size(), thisConfig.getMapConfigs().size());
        assertEquals(otherConfig.getListConfigs().size(), thisConfig.getListConfigs().size());
        assertEquals(otherConfig.getListenerConfigs().size(), thisConfig.getListenerConfigs().size());
        assertPropertiesEquals(thisConfig.getProperties(), otherConfig.getProperties());
    }

    private Properties buildPropertiesWithDefaults() {
        Properties defaults = new Properties();
        defaults.setProperty("key1", "value1");
        Properties configProperties = new Properties(defaults);
        configProperties.setProperty("key2", "value2");
        return configProperties;
    }
}
