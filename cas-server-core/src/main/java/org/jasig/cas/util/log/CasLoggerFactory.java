/*
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.cas.util.log;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of {@link org.slf4j.ILoggerFactory} that is looked up via the
 * {@link org.slf4j.impl.StaticLoggerBinder} of CAS itself. It is responsible for
 * creating {@link org.slf4j.Logger} instances and passing them back to the slf4j engine.
 * @author Misagh Moayyed
 * @since 4.1
 */
public final class CasLoggerFactory implements ILoggerFactory {

    private static final String PACKAGE_TO_SCAN = "org.slf4j.impl";

    private final Map<String, CasDelegatingLogger> loggerMap;
    private final Reflections reflections;

    /**
     * Instantiates a new Cas logger factory.
     * Configures the reflection scanning engine to be prepared to scan <code>org.slf4j.impl</code>
     * in order to find other avaliable factories.
     */
    public CasLoggerFactory() {
        this.loggerMap = new ConcurrentHashMap<String, CasDelegatingLogger>();
        final Set<URL> set = ClasspathHelper.forPackage(PACKAGE_TO_SCAN);
        this.reflections = new Reflections(new ConfigurationBuilder().addUrls(set).setScanners(new SubTypesScanner()));
    }

    /**
     * {@inheritDoc}
     * <p>Attempts to find the <strong>real</strong> <code>Logger</code> istance that
     * is doing the heavy lifting and routes the request to an instance of
     * {@link org.jasig.cas.util.log.CasDelegatingLogger}. The instance is cached.</p>
     */
    @Override
    public Logger getLogger(final String name) {
        synchronized (loggerMap) {
            if (!loggerMap.containsKey(name)) {
                final Logger logger = getRealLoggerInstance(name);
                loggerMap.put(name, new CasDelegatingLogger(name, logger));
            }
            return loggerMap.get(name);
        }
    }

    /**
     * Find the actual <code>Logger</code> instance that is available on the classpath.
     * This is usually the logger adapter that is provided by the real logging framework,
     * such as log4j, etc. The method will scan the runtime to find logger factories that
     * are of type {@link org.slf4j.ILoggerFactory}. It will remove itself from this list
     * first and then attempts to locate the next best factory from which real logger instances
     * can be created.
     * @param name requested logger name
     * @return the logger instance created by the logger factory available on the classpath during runtime, or null.
     */
    private Logger getRealLoggerInstance(final String name) {
        try {
            final Set<Class<? extends ILoggerFactory>> subTypesOf = this.reflections.getSubTypesOf(ILoggerFactory.class);
            subTypesOf.remove(this.getClass());

            if (subTypesOf.size() > 1) {
                System.err.println("Multiple ILoggerFactory bindings are found on the classpath:");
                for (final Class<? extends ILoggerFactory> c : subTypesOf) {
                    System.err.println("- " + c.getCanonicalName());
                }
                System.err.println("CAS will select the ILoggerFactory: " + subTypesOf.iterator().next());
            }

            if (subTypesOf.size() > 0) {
                final Class<? extends ILoggerFactory> factoryClass = subTypesOf.iterator().next();
                final ILoggerFactory factInstance = factoryClass.newInstance();
                return factInstance.getLogger(name);
            }

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return null;
    }

}
