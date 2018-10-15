/**
 * ==========================================================================
 * Copyright © 2015-2018 Cristiano Gavião, C8 Technology ME.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cristiano Gavião (cvgaviao@c8tech.com.br)- initial API and implementation
 * ==========================================================================
 */
package br.com.c8tech.tools.maven.plugin.osgi.repository.it;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.5.4" })
public abstract class AbstractIntegrationTest {

    @Rule
    public final TestResources resources = new TestResources();

    public final TestProperties properties = new TestProperties();

    public final MavenRuntime mavenRuntime;

    public final MavenRuntimeBuilder mavenRuntimeBuilder;

    public AbstractIntegrationTest(MavenRuntimeBuilder verifierBuilder)
            throws Exception {
        this.mavenRuntimeBuilder = verifierBuilder;
        // this.mavenRuntime =
        // verifierBuilder.withCliOptions("-X","-U","-B").build();
        this.mavenRuntime = verifierBuilder.withCliOptions("-B").build();
    }

}
