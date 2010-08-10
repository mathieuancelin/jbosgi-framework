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
package org.jboss.test.osgi.container.internal.loading;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.ArrayList;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.BundleRevision;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.bundle.Revision;
import org.jboss.osgi.container.bundle.SystemBundle;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.jboss.osgi.container.plugin.internal.SystemPackagesPluginImpl;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.container.simple.bundleC.SimpleActivator;
import org.jboss.test.osgi.container.simple.bundleC.SimpleService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class ModuleClassLoaderTestCase 
{
   static ModuleClassLoader classLoader;
   
   @BeforeClass
   public static void beforeClass() throws Exception
   {
      // Mock the BundleManager to return the {@link FrameworkState}
      BundleManager bundleManager = Mockito.mock(BundleManager.class);
      FrameworkState frameworkState = Mockito.mock(FrameworkState.class);
      Mockito.when(bundleManager.getFrameworkState()).thenReturn(frameworkState);
      
      // Mock the BundleManager to return an instance of the {@link SystemPackagesPlugin}
      SystemPackagesPluginImpl sysPackagesPlugin = new SystemPackagesPluginImpl(bundleManager);
      Mockito.when(bundleManager.getPlugin(SystemPackagesPlugin.class)).thenReturn(sysPackagesPlugin);

      // Get the resolver module for the SystemBundle
      SystemBundle systemBundle = new SystemBundle(bundleManager);
      XModule resModule = systemBundle.getResolverModule();
      Mockito.when(bundleManager.getSystemBundle()).thenReturn(systemBundle);
      
      // Create the Framework module
      ModuleManager moduleManager = new ModuleManager(bundleManager);
      ModuleSpec moduleSpec = moduleManager.createFrameworkModule(resModule);
      
      // Bundle-Version: 1.0.0
      // Bundle-SymbolicName: simple-bundle
      // Bundle-Activator: org.jboss.osgi.msc.framework.simple.bundle.SimpleActivator
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
      archive.addClasses(SimpleService.class, SimpleActivator.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleVersion("1.0.0");
            builder.addBundleActivator(SimpleActivator.class);
            return builder.openStream();
         }
      });
      
      // Create the XModule 
      resModule = Mockito.mock(XModule.class);
      Mockito.when(resModule.getModuleId()).thenReturn(new Long(1));
      Mockito.when(resModule.getName()).thenReturn(archive.getName());
      Mockito.when(resModule.getVersion()).thenReturn(Version.parseVersion("1.0.0"));
      Mockito.when(resModule.getWires()).thenReturn(new ArrayList<XWire>());
      AbstractBundle bundleState = Mockito.mock(AbstractBundle.class);
      Mockito.when(resModule.getAttachment(Bundle.class)).thenReturn(bundleState);
      BundleRevision bundleRevision = Mockito.mock(BundleRevision.class);
      Mockito.when(resModule.getAttachment(Revision.class)).thenReturn(bundleRevision);
      
      // Create the ModuleSpec and the Module
      VirtualFile rootFile = OSGiTestHelper.toVirtualFile(archive);
      moduleSpec = moduleManager.createModuleSpec(resModule, rootFile);
      Module module = moduleManager.loadModule(moduleSpec.getIdentifier());
      classLoader = module.getClassLoader();
   }
   
   @Test
   public void testLoadBundleActivator() throws Exception
   {
      Class<?> result = classLoader.loadClass(SimpleActivator.class.getName());
      assertNotNull("Class loaded", result);
      assertTrue("Is assignable", BundleActivator.class.isAssignableFrom(result));
   }

   @Test
   public void testLoadLogServiceFail() throws Exception
   {
      try
      {
         classLoader.loadClass(LogService.class.getName());
         fail("ClassNotFoundException expected");
      }
      catch (ClassNotFoundException ex)
      {
         // expected
      }
   }
}