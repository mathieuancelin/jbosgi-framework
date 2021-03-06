/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.AutoInstallProvider;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.spi.util.StringPropertyReplacer;
import org.jboss.osgi.spi.util.StringPropertyReplacer.PropertyProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plugin that installs/starts bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class DefaultAutoInstallProvider extends AbstractPluginService<AutoInstallProvider> implements AutoInstallProvider {

    // Provide logging
    static final Logger log = Logger.getLogger(DefaultAutoInstallProvider.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();

    static void addService(ServiceTarget serviceTarget) {
        DefaultAutoInstallProvider service = new DefaultAutoInstallProvider();
        ServiceBuilder<AutoInstallProvider> builder = serviceTarget.addService(Services.AUTOINSTALL_PROVIDER, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_INIT);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultAutoInstallProvider() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        try {
            List<URL> autoInstall = new ArrayList<URL>();
            List<URL> autoStart = new ArrayList<URL>();

            BundleManager bundleManager = injectedBundleManager.getValue();
            String propValue = (String) bundleManager.getProperty(Constants.PROPERTY_AUTO_INSTALL_URLS);
            if (propValue != null) {
                for (String path : propValue.split(",")) {
                    URL url = toURL(bundleManager, path.trim());
                    if (url != null) {
                        autoInstall.add(url);
                    }
                }
            }
            propValue = (String) bundleManager.getProperty(Constants.PROPERTY_AUTO_START_URLS);
            if (propValue != null) {
                for (String path : propValue.split(",")) {
                    URL url = toURL(bundleManager, path.trim());
                    if (url != null) {
                        autoStart.add(url);
                    }
                }
            }
            ServiceTarget serviceTarget = context.getChildTarget();
            installBundles(serviceTarget, autoInstall, autoStart);
        } catch (BundleException ex) {
            throw new IllegalStateException("Cannot start auto install bundles", ex);
        }
    }

    @Override
    public DefaultAutoInstallProvider getValue() {
        return this;
    }

    private void installBundles(ServiceTarget serviceTarget, final List<URL> autoInstall, final List<URL> autoStart) throws BundleException {

        // Add the autoStart bundles to autoInstall
        autoInstall.addAll(autoStart);

        final BundleManager bundleManager = injectedBundleManager.getValue();
        final Map<ServiceName, Deployment> pendingServices = new HashMap<ServiceName, Deployment>();

        // Install autoInstall bundles
        for (URL url : autoInstall) {
            BundleInfo info = BundleInfo.createBundleInfo(url);
            Deployment dep = DeploymentFactory.createDeployment(info);
            dep.setAutoStart(autoStart.contains(url));

            ServiceName serviceName = bundleManager.installBundle(serviceTarget, dep);
            pendingServices.put(serviceName, dep);
        }

        // Install a service that has a dependency on all pending bundle INSTALLED services
        ServiceName servicesInstalled = Services.AUTOINSTALL_PROVIDER.append("INSTALLED");
        ServiceBuilder<Void> builder = serviceTarget.addService(servicesInstalled, new AbstractService<Void>() {
            public void start(StartContext context) throws StartException {
                log.debugf("Auto bundles installed");
            }
        });
        builder.addDependencies(pendingServices.keySet());
        builder.install();

        // Install a service that starts the bundles
        builder = serviceTarget.addService(Services.AUTOINSTALL_PROVIDER_COMPLETE, new AbstractService<Void>() {
            public void start(StartContext context) throws StartException {
                for (Deployment dep : pendingServices.values()) {
                    if (dep.isAutoStart()) {
                        Bundle bundle = dep.getAttachment(Bundle.class);
                        try {
                            bundle.start();
                        } catch (BundleException ex) {
                            log.errorf(ex, "Cannot start persistent bundle: %s", bundle);
                        }
                    }
                }
                log.debugf("Auto bundles started");
            }
        });
        builder.addDependencies(servicesInstalled);
        builder.install();
    }

    private URL toURL(final BundleManager bundleManager, final String path) {

        URL pathURL = null;
        PropertyProvider provider = new PropertyProvider() {
            @Override
            public String getProperty(String key) {
                return (String) bundleManager.getProperty(key);
            }
        };
        String realPath = StringPropertyReplacer.replaceProperties(path, provider);
        try {
            pathURL = new URL(realPath);
        } catch (MalformedURLException ex) {
            // ignore
        }

        if (pathURL == null) {
            try {
                File file = new File(realPath);
                if (file.exists())
                    pathURL = file.toURI().toURL();
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException("Invalid path: " + realPath, ex);
            }
        }

        if (pathURL == null)
            throw new IllegalArgumentException("Invalid path: " + realPath);

        return pathURL;
    }
}