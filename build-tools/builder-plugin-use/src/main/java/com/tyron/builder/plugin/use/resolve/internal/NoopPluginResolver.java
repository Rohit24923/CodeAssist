/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tyron.builder.plugin.use.resolve.internal;

import com.tyron.builder.api.Plugin;
import com.tyron.builder.api.internal.plugins.DefaultPotentialPluginWithId;
import com.tyron.builder.api.internal.plugins.PluginRegistry;
import com.tyron.builder.plugin.use.PluginId;
import com.tyron.builder.plugin.use.internal.DefaultPluginId;
import com.tyron.builder.plugin.management.internal.InvalidPluginRequestException;
import com.tyron.builder.plugin.management.internal.PluginRequestInternal;

// Used for testing the plugins DSL
public class NoopPluginResolver implements PluginResolver {

    public static final PluginId NOOP_PLUGIN_ID = DefaultPluginId.of("noop");
    private final PluginRegistry pluginRegistry;

    public NoopPluginResolver(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getId().equals(NOOP_PLUGIN_ID)) {
            result.found("noop resolver", new SimplePluginResolution(DefaultPotentialPluginWithId.of(NOOP_PLUGIN_ID, pluginRegistry.inspect(NoopPlugin.class))));
        }
    }

    public static class NoopPlugin implements Plugin<Object> {
        @Override
        public void apply(Object target) {
            // do nothing
        }
    }

}
