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

import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;


/**
 * Represents the INSTALLED state of a user bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class UserBundleService<T extends UserBundleState> extends AbstractBundleService<T> {

    private final Deployment initialDeployment;
    
    UserBundleService(T bundleState, Deployment dep) {
        super(bundleState);
        this.initialDeployment = dep;
    }
    
    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        BundleStorageState storageState = null;
        try {
            T bundleState = getBundleState();
            Deployment dep = initialDeployment;
            dep.addAttachment(Bundle.class, bundleState);
            OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
            storageState = bundleState.createStorageState(dep);
            bundleState.createResolverModule(dep);
            bundleState.createRevision(dep);
            bundleState.initUserBundleState(metadata);
            validateBundle(bundleState, metadata);
            processNativeCode(bundleState, dep);
            getBundleManager().addBundle(bundleState);
            bundleState.changeState(INSTALLED);
            addToResolver(bundleState);
        } catch (BundleException ex) {
            if (storageState != null)
                storageState.deleteBundleStorage();
            throw new StartException(ex);
        }
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        getBundleManager().uninstallBundle(getBundleState(), Bundle.STOP_TRANSIENT);
    }

    private void validateBundle(UserBundleState userBundle, OSGiMetaData metadata) throws BundleException {
        if (metadata.getBundleManifestVersion() > 1) {
            new BundleValidatorR4().validateBundle(userBundle, metadata);
        } else {
            new BundleValidatorR3().validateBundle(userBundle, metadata);
        }
    }

    // Process the Bundle-NativeCode header if there is one
    private void processNativeCode(UserBundleState userBundle, Deployment dep) {
        OSGiMetaData metadata = userBundle.getOSGiMetaData();
        if (metadata.getBundleNativeCode() != null) {
            FrameworkState frameworkState = userBundle.getFrameworkState();
            NativeCodePlugin nativeCodePlugin = frameworkState.getNativeCodePlugin();
            nativeCodePlugin.deployNativeCode(dep);
        }
    }

    private void addToResolver(UserBundleState userBundle) {
        if (userBundle.isSingleton()) {
            for (AbstractBundleState aux : getBundleManager().getBundles(getSymbolicName(), null)) {
                if (aux != userBundle && aux.isSingleton()) {
                    log.infof("No resolvable singleton bundle: %s", this);
                    return;
                }
            }
        }
        FrameworkState frameworkState = userBundle.getFrameworkState();
        ResolverPlugin resolverPlugin = frameworkState.getResolverPlugin();
        XModule resModule = userBundle.getResolverModule();
        resolverPlugin.addModule(resModule);
    }
}