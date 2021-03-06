/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.osgi.framework.internal;

import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.PackageAttribute;
import org.jboss.osgi.metadata.ParameterizedAttribute;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.osgi.framework.Constants.EXTENSION_BOOTCLASSPATH;
import static org.osgi.framework.Constants.EXTENSION_DIRECTIVE;
import static org.osgi.framework.Constants.EXTENSION_FRAMEWORK;
import static org.osgi.framework.Constants.PACKAGE_SPECIFICATION_VERSION;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.VERSION_ATTRIBUTE;

/**
 * A bundle validator for OSGi R4.
 * 
 * @author thomas.diesler@jboss.com
 * @version $Revision: 1.1 $
 */
final class BundleValidatorR4 implements BundleValidator {

    @SuppressWarnings("deprecation")
    public void validateBundle(UserBundleState userBundle, OSGiMetaData osgiMetaData) throws BundleException {

        // Missing Bundle-SymbolicName
        final String symbolicName = osgiMetaData.getBundleSymbolicName();
        if (symbolicName == null)
            throw new BundleException("Missing Bundle-SymbolicName in: " + userBundle);

        // Bundle-ManifestVersion value not equal to 2, unless the Framework specifically
        // recognizes the semantics of a later release.
        int manifestVersion = osgiMetaData.getBundleManifestVersion();
        if (manifestVersion > 2)
            throw new BundleException("Unsupported manifest version " + manifestVersion + " for " + userBundle);

        // Multiple imports of a given package.
        // Specification-version and version specified together (for the same package(s)) but with different values
        List<PackageAttribute> importPackages = osgiMetaData.getImportPackages();
        if (importPackages != null) {
            Set<String> packages = new HashSet<String>();
            for (PackageAttribute packageAttribute : importPackages) {
                String packageName = packageAttribute.getAttribute();
                if (packages.contains(packageName))
                    throw new BundleException("Duplicate import of package " + packageName + " for " + userBundle);
                packages.add(packageName);

                if (packageName.startsWith("java."))
                    throw new BundleException("Not allowed to import java.* for " + userBundle);

                String version = packageAttribute.getAttributeValue(VERSION_ATTRIBUTE, String.class);
                String specificationVersion = packageAttribute.getAttributeValue(PACKAGE_SPECIFICATION_VERSION, String.class);
                if (version != null && specificationVersion != null && version.equals(specificationVersion) == false)
                    throw new BundleException(packageName + " version and specification version should be the same in: " + userBundle);
            }
        }

        // Export or import of java.*.
        // Specification-version and version specified together (for the same package(s)) but with different values
        // The export statement must not specify an explicit bundle symbolic name nor bundle version
        List<PackageAttribute> exportPackages = osgiMetaData.getExportPackages();
        if (exportPackages != null) {
            for (PackageAttribute packageAttr : exportPackages) {
                String packageName = packageAttr.getAttribute();
                if (packageName.startsWith("java."))
                    throw new BundleException("Not allowed to export java.* for " + userBundle);
                
                String versionAttr = packageAttr.getAttributeValue(Constants.VERSION_ATTRIBUTE, String.class);
                String specificationAttr = packageAttr.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION, String.class);
                if (versionAttr != null && specificationAttr != null && versionAttr.equals(specificationAttr) == false)
                    throw new BundleException(packageName + " version and specification version should be the same in: " + userBundle);
                
                String symbolicNameAttr = packageAttr.getAttributeValue(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, String.class);
                if (symbolicNameAttr != null)
                    throw new BundleException(packageName + " must not specify an explicit bundle symbolic name in: " + userBundle);
                
                String bundleVersionAttr = packageAttr.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE, String.class);
                if (bundleVersionAttr != null)
                    throw new BundleException(packageName + " must not specify an explicit bundle version in: " + userBundle);
            }
        }

        // A bundle with a dynamic imported package having different values for version and specification-version attributes must fail to install
        List<PackageAttribute> dynamicImports = osgiMetaData.getDynamicImports();
        if (dynamicImports != null) {
            for (PackageAttribute packageAttr : dynamicImports) {
                String packageName = packageAttr.getAttribute();
                String versionAttr = packageAttr.getAttributeValue(Constants.VERSION_ATTRIBUTE, String.class);
                String specificationAttr = packageAttr.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION, String.class);
                if (versionAttr != null && specificationAttr != null && versionAttr.equals(specificationAttr) == false)
                    throw new BundleException(packageName + " version and specification version should be the same in: " + userBundle);
            }
        }
        
        // Installing a bundle that has the same symbolic name and version as an already installed bundle.
        for (AbstractBundleState aux : userBundle.getBundleManager().getBundles()) {
            if (userBundle.getCanonicalName().equals(aux.getCanonicalName())) {
                throw new BundleException("Cannot install bundle, name and version already installed: " + aux);
            }
        }

        // Verify Fragment-Host header
        if (userBundle.isFragment()) {
            ParameterizedAttribute hostAttr = osgiMetaData.getFragmentHost();
            String fragmentHost = hostAttr.getAttribute();
            String extension = hostAttr.getDirectiveValue(EXTENSION_DIRECTIVE, String.class);
            if (extension != null) {
                if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(fragmentHost) == false)
                    throw new BundleException("Invalid Fragment-Host for extension fragment: " + userBundle);

                if (EXTENSION_BOOTCLASSPATH.equals(extension))
                    throw new UnsupportedOperationException("Boot classpath extension not supported");

                if (EXTENSION_FRAMEWORK.equals(extension))
                    throw new UnsupportedOperationException("Framework extension not supported");
            }
        }

        // [TODO] Duplicate attribute or duplicate directive (except in the Bundle-Native code clause).

        // [TODO] Export-Package with a mandatory attribute that is not defined.

        // [TODO] Updating a bundle to a bundle that has the same symbolic name and version as another installed bundle.

        // [TODO] Any syntactic error (for example, improperly formatted version or bundle symbolic name, unrecognized directive
        // value, etc.).

        // [TODO] The manifest lists a OSGI-INF/permission.perm file but no such file is present.

        // [TODO] Requiring the same bundle symbolic name more than once
    }
}
