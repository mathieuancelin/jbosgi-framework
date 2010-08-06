/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.container.service.packageadmin;

import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.container.bundle.support.a.ObjectA;
import org.jboss.test.osgi.container.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.container.bundle.support.x.ObjectX;
import org.jboss.test.osgi.container.packageadmin.exported.Exported;
import org.jboss.test.osgi.container.packageadmin.importexport.ImportExport;
import org.jboss.test.osgi.container.packageadmin.optimporter.Importing;
import org.jboss.test.osgi.container.service.support.a.PA;
import org.jboss.test.osgi.container.service.support.b.Other;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

/**
 * Test PackageAdmin service.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class PackageAdminTestCase extends OSGiFrameworkTest
{
   @Test
   public void testGetBundle() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("smoke-assembled", "/bundles/smoke/smoke-assembled", PA.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         Class<?> paClass = assertLoadClass(bundleA, PA.class.getName());

         PackageAdmin pa = getPackageAdmin();

         Bundle found = pa.getBundle(paClass);
         assertSame(bundleA, found);

         Bundle notFound = pa.getBundle(getClass());
         assertNull(notFound);

         Archive<?> assemblyB = assembleArchive("simple", "/bundles/simple/simple-bundle1", Other.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            Class<?> otherClass = assertLoadClass(bundleB, Other.class.getName());

            found = pa.getBundle(otherClass);
            assertSame(bundleB, found);
         }
         finally
         {
            bundleB.uninstall();
         }
      }
      finally
      {
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetBundle2() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
      try
      {
         bundleA.start();
         bundleB.start();

         Class<?> clA = bundleA.loadClass(Exported.class.getName());
         Class<?> clB = bundleB.loadClass(Exported.class.getName());
         assertNotSame(clA, clB);

         assertEquals(bundleA, pa.getBundle(clA));
         assertEquals(bundleB, pa.getBundle(clB));

         assertNull(pa.getBundle(String.class));
      }
      finally
      {
         bundleB.uninstall();
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetBundles() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleC = installBundle(assembleArchive("exporter_3", "/bundles/package-admin/exporter_3", Exported.class));
      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
      Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));
      try
      {
         bundleC.start();
         bundleA.start();
         bundleB.start();
         bundleR.start();

         assertNull(pa.getBundles("hello", null));

         Bundle[] rb = pa.getBundles("Requiring", null);
         assertEquals(1, rb.length);
         assertEquals(bundleR, rb[0]);

         Bundle[] eb = pa.getBundles("Exporter", null);
         assertEquals(3, eb.length);
         assertEquals("The first one in the list should have the highest version", bundleC, eb[0]);
         assertEquals(bundleB, eb[1]);
         assertEquals(bundleA, eb[2]);

         Bundle[] eb2 = pa.getBundles("Exporter", "1.0.0");
         assertTrue(Arrays.equals(eb, eb2));
         Bundle[] eb3 = pa.getBundles("Exporter", "[1.0.0, 4.0]");
         assertTrue(Arrays.equals(eb, eb3));
         Bundle[] eb4 = pa.getBundles("Exporter", "[1.0.0, 2.0)");
         assertEquals(1, eb4.length);
         assertEquals(bundleA, eb4[0]);
         Bundle[] eb5 = pa.getBundles("Exporter", "[2.0.0, 2.0.0]");
         assertEquals(1, eb5.length);
         assertEquals(bundleB, eb5[0]);
      }
      finally
      {
         bundleR.uninstall();
         bundleC.uninstall();
         bundleB.uninstall();
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetBundleType() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();

      assertEquals(0, pa.getBundleType(getSystemContext().getBundle(0)));

      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      try
      {
         assertEquals(0, pa.getBundleType(bundleA));
      }
      finally
      {
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetExportedPackagesByBundle() throws Exception
   {
      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      Bundle bundleB = installBundle(assembleArchive("opt-importer", "/bundles/package-admin/opt-importer", Importing.class));

      bundleA.start();
      bundleB.start();

      try
      {
         PackageAdmin pa = getPackageAdmin();
         ExportedPackage[] pkgsA = pa.getExportedPackages(bundleA);
         assertEquals(1, pkgsA.length);
         ExportedPackage pkgA = pkgsA[0];
         assertSame(bundleA, pkgA.getExportingBundle());
         assertEquals(Exported.class.getPackage().getName(), pkgA.getName());
         assertEquals(1, pkgA.getImportingBundles().length);
         assertSame(bundleB, pkgA.getImportingBundles()[0]);
         assertEquals(Version.parseVersion("1.7"), pkgA.getVersion());

         assertNull(pa.getExportedPackages(bundleB));

         Bundle bundleC = installBundle(assembleArchive("import-export", "/bundles/package-admin/import-export", ImportExport.class));
         try
         {
            bundleC.start();
            ExportedPackage[] allPkgs = pa.getExportedPackages((Bundle)null);
            List<String> pall = new ArrayList<String>();
            for (ExportedPackage ep : allPkgs)
            {
               pall.add(ep.getName());
            }
            assertTrue(pall.contains(Exported.class.getPackage().getName()));
            assertTrue(pall.contains(ImportExport.class.getPackage().getName()));
            assertTrue(pall.contains("org.osgi.framework"));
         }
         finally
         {
            bundleC.uninstall();
         }
      }
      finally
      {
         bundleB.uninstall();
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetExportedPackagesSystemBundle() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      ExportedPackage[] sysPkgs = pa.getExportedPackages(getSystemContext().getBundle(0));
      List<String> pall = new ArrayList<String>();
      for (ExportedPackage ep : sysPkgs)
      {
         pall.add(ep.getName());
      }
      assertTrue(pall.contains("org.osgi.framework"));
   }

   @Test
   public void testGetExportedPackagesUnknownBundle() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      try
      {
         pa.getExportedPackages(Mockito.mock(Bundle.class));
         fail("Should have thrown an Illegal Argument Exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }
   }

   @Test
   public void testGetExportedPackage() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      try
      {
         bundleA.start();

         ExportedPackage ep = pa.getExportedPackage(Exported.class.getPackage().getName());
         assertSame(bundleA, ep.getExportingBundle());
         assertEquals(Version.parseVersion("1.7"), ep.getVersion());

         Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
         try
         {
            bundleB.start();

            ExportedPackage ep2 = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertSame(bundleB, ep2.getExportingBundle());
            assertEquals(Version.parseVersion("2"), ep2.getVersion());

            assertNull(pa.getExportedPackage("org.foo.bar"));
            assertEquals(getSystemContext().getBundle(0),
                  pa.getExportedPackage("org.osgi.framework").getExportingBundle());
         }
         finally
         {
            bundleB.uninstall();
         }
      }
      finally
      {
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetExportedPackagesByName() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      try
      {
         bundleA.start();
         ExportedPackage epA = pa.getExportedPackage(Exported.class.getPackage().getName());
         assertSame(bundleA, epA.getExportingBundle());

         Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
         try
         {
            bundleB.start();
            ExportedPackage epB = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertSame(bundleB, epB.getExportingBundle());
            assertNotSame(epA, epB);

            ExportedPackage[] eps = pa.getExportedPackages(Exported.class.getPackage().getName());
            assertEquals(2, eps.length);
            List<Version> versions = new ArrayList<Version>();
            for (ExportedPackage ep : eps)
            {
               assertEquals(Exported.class.getPackage().getName(), ep.getName());
               versions.add(ep.getVersion());
            }
            assertTrue(versions.contains(epA.getVersion()));
            assertTrue(versions.contains(epB.getVersion()));

            assertNull(pa.getExportedPackage("org.foo.bar"));
            ExportedPackage[] esp = pa.getExportedPackages("org.osgi.framework");
            assertEquals(1, esp.length);
            assertEquals(getSystemContext().getBundle(0), esp[0].getExportingBundle());
         }
         finally
         {
            bundleB.uninstall();
         }
      }
      finally
      {
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetFragments() throws Exception
   {
      System.out.println("FIXME [JBOSGI-369] PackageAdmin Fragment Support");
   }

   @Test
   public void testGetHosts() throws Exception
   {
      System.out.println("FIXME [JBOSGI-369] PackageAdmin Fragment Support");
   }

   @Test
   public void testGetRequiredBundles() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
      Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));
      try
      {
         bundleA.start();
         bundleB.start();
         bundleR.start();

         RequiredBundle[] rqbs = pa.getRequiredBundles("Exporter");
         assertEquals(2, rqbs.length);

         Set<String> bsns = new HashSet<String>();
         Set<Bundle> actualBundles = new HashSet<Bundle>();
         for (RequiredBundle rb : rqbs)
         {
            bsns.add(rb.getSymbolicName());
            actualBundles.add(rb.getBundle());
         }

         assertEquals(Collections.singleton("Exporter"), bsns);

         Set<Bundle> expectedBundles = new HashSet<Bundle>();
         expectedBundles.add(bundleA);
         expectedBundles.add(bundleB);
         assertEquals(expectedBundles, actualBundles);
         for (RequiredBundle b : rqbs)
         {
            if (b.getBundle().equals(bundleA))
            {
               assertEquals(Version.parseVersion("1"), b.getVersion());
               assertEquals(1, b.getRequiringBundles().length);
               assertEquals(bundleR, b.getRequiringBundles()[0]);
            }
            else if (b.getBundle().equals(bundleB))
            {
               assertEquals(Version.parseVersion("2"), b.getVersion());
               assertEquals(0, b.getRequiringBundles().length);
            }
            else
               fail("Unexpected bundle");
         }

         assertNull(pa.getRequiredBundles("foobar"));
      }
      finally
      {
         bundleR.uninstall();
         bundleB.uninstall();
         bundleA.uninstall();
      }
   }

   @Test
   public void testRefreshPackages() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", Importing.class));

      try
      {
         bundleI.start();
         assertLoadClass(bundleI, Exported.class.getName());
         Assert.assertNotNull(getImportedFieldValue(bundleI));

         bundleE.uninstall();
         bundleI.stop();
         bundleI.start();
         assertLoadClass(bundleI, Exported.class.getName());
         Assert.assertNotNull("The stale bundle E should still be available for classloading so the imported field should have a value",
               getImportedFieldValue(bundleI));

         pa.refreshPackages(new Bundle[] { bundleE });
         Assert.assertEquals(Bundle.ACTIVE, bundleI.getState());
         assertLoadClassFail(bundleI, Exported.class.getName());
         assertNull("Now that the packages are refreshed, bundle E should be no longer available for classloading",
               getImportedFieldValue(bundleI));
      }
      finally
      {
         bundleI.uninstall();
         if (bundleE.getState() != Bundle.UNINSTALLED)
            bundleE.uninstall();
      }
   }

   @Test
   public void testRefreshPackagesNull() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Archive<?> assemblyx = assembleArchive("bundlex", "/bundles/update/update-bundlex", ObjectX.class);
      Archive<?> assembly1 = assembleArchive("bundle1", new String[] { "/bundles/update/update-bundle1", "/bundles/update/classes1" });
      Archive<?> assembly2 = assembleArchive("bundle2", new String[] { "/bundles/update/update-bundle102", "/bundles/update/classes2" });

      Bundle bundleA = installBundle(assembly1);
      Bundle bundleX = installBundle(assemblyx);
      try
      {
         BundleContext systemContext = getFramework().getBundleContext();
         int beforeCount = systemContext.getBundles().length;

         bundleA.start();
         bundleX.start();

         assertBundleState(Bundle.ACTIVE, bundleA.getState());
         assertBundleState(Bundle.ACTIVE, bundleX.getState());
         assertEquals(Version.parseVersion("1.0.0"), bundleA.getVersion());
         assertEquals("update-bundle1", bundleA.getSymbolicName());
         assertLoadClass(bundleA, ObjectA.class.getName());
         assertLoadClassFail(bundleA, ObjectA2.class.getName());
         assertLoadClass(bundleX, ObjectA.class.getName());
         assertLoadClassFail(bundleX, ObjectA2.class.getName());

         Class<?> cls = bundleX.loadClass(ObjectX.class.getName());

         bundleA.update(toVirtualFile(assembly2).openStream());
         assertBundleState(Bundle.ACTIVE, bundleA.getState());
         assertBundleState(Bundle.ACTIVE, bundleX.getState());
         assertEquals(Version.parseVersion("1.0.0"), bundleA.getVersion());
         // Assembly X depends on a package in the bundle, so don't update the packages yet.
         assertLoadClass(bundleA, ObjectA.class.getName());
         assertLoadClassFail(bundleA, ObjectA2.class.getName());
         assertLoadClass(bundleX, ObjectA.class.getName());
         assertLoadClassFail(bundleX, ObjectA2.class.getName());
         assertSame(cls, bundleX.loadClass(ObjectX.class.getName()));

         pa.refreshPackages(null);
         assertBundleState(Bundle.ACTIVE, bundleA.getState());
         assertBundleState(Bundle.ACTIVE, bundleX.getState());
         assertEquals(Version.parseVersion("1.0.2"), bundleA.getVersion());
         assertLoadClass(bundleA, ObjectA2.class.getName());
         assertLoadClassFail(bundleA, ObjectA.class.getName());
         assertLoadClass(bundleX, ObjectA2.class.getName());
         assertLoadClassFail(bundleX, ObjectA.class.getName());

         Class<?> cls2 = bundleX.loadClass(ObjectX.class.getName());
         assertNotSame("Should have loaded a new class", cls, cls2);

         int afterCount = systemContext.getBundles().length;
         assertEquals("Bundle count", beforeCount, afterCount);
      }
      finally
      {
         bundleX.uninstall();
         bundleA.uninstall();
      }
   }

   private Object getImportedFieldValue(Bundle bundleI) throws Exception
   {
      Class<?> iCls = bundleI.loadClass(Importing.class.getName());
      Object importing = iCls.newInstance();
      Field field = iCls.getDeclaredField("imported");
      return field.get(importing);
   }

   @Test
   public void testResolveBundles() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }
}