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
package org.jboss.test.osgi.modules.b;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.test.osgi.modules.ModuleActivator;

public class CircularityActivator implements ModuleActivator {

    @Override
    public boolean start() {
        Module module = ((ModuleClassLoader) getClass().getClassLoader()).getModule();
        try {
            ModuleLoader moduleLoader = module.getModuleLoader();
            Module moduleA = moduleLoader.loadModule(ModuleIdentifier.create("moduleA"));
            moduleA.getClassLoader().loadClass("org.jboss.test.osgi.modules.a.CircularityError");
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot activate module: " + module);
        }
    }
}
