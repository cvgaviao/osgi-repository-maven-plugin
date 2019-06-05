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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions("3.6.0")
public class GenerateRepositoryFromDependenciesIntegrationTest
        extends AbstractIntegrationTest {

    public GenerateRepositoryFromDependenciesIntegrationTest(
            MavenRuntimeBuilder builder) throws Exception {
        super(builder);
    }

    @Test()
    public void testFailureWhenNoDependenciesDeclared() throws Exception {
        File basedir = resources
                .getBasedir("it-project--fail-when-no-deps-declared");

        MavenExecutionResult result = mavenRuntime.forProject(basedir)
                .execute("clean", "package");

        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test");
        result.assertLogText(
                "No artifact needs to be cached from a maven repository for project");
        // result.assertLogText(
        // "Skipping downloading artifacts from p2 repositories");
        result.assertLogText(
                "Setting up generation of the OSGi p2 repository for project");
        result.assertLogText("Skipping p2 repository generation");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file for project osgi-repository.it.test");
        result.assertLogText(
                "There was any artifact set in order to generate the OSGi repository index for project osgi-repository.it.test");
        result.assertLogText(
                "Starting to pack the items of OSGi repository archive for project osgi-repository.it.test");
    }

    @Test
    public void testFailureWithWrongPackage() throws Exception {
        File basedir = resources.getBasedir("it-project--fail-wrong-packaging");

        MavenExecutionResult result = mavenRuntime.forProject(basedir)
                .execute("clean", "package");
        result.assertLogText("[ERROR] Failed to execute goal");
        result.assertLogText(String.format(
                "The project '%s' has a packaging not allowed by this plugin. Allowed packagings are '%s'.",
                "br.com.c8tech.tools:osgi-repository.unit.test:jar:1.0.0",
                "[osgi.repository]"));
    }

    @Test
    public void testWhenEmbeddingAndNotGenerateP2() throws Exception {
        File repo = new File("target/test-classes/composite/repository/1.0.0")
                .getCanonicalFile();
        assertThat(repo.exists() && repo.isDirectory()).isTrue();

        MavenRuntime mavenRuntime2 = mavenRuntimeBuilder
                .withCliOptions(
                        "-Dp2ArtifactSetRepositoryUrl=" + repo.toURI().toURL().toExternalForm())
                .build();
        File basedir = resources.getBasedir("it-project--embedAndNoP2");
        MavenExecutionResult result = mavenRuntime2.forProject(basedir)
                .execute("clean", "package");

        result.assertErrorFreeLog();
        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test");
        // result.assertLogText(
        // "Skipping downloading artifacts from p2 repositories for project
        // osgi-repository.it.test since there are any artifact declared in
        // pom.");
        result.assertLogText(
                "Finished copying of 1 artifact from maven repositories.");
        result.assertLogText("Setting up generation of the OSGi p2 repository");
        result.assertLogText("Skipping p2 repository generation");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file");
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText(
                "Repository index file was successfully generated at");
        result.assertLogText(
                "Setting up generation of the OSGi Repository archive for project");
        result.assertLogText(
                "Starting to pack the items of OSGi repository archive for project");
        result.assertLogText("Included file: plugins/slf4j-simple-1.7.12.jar");
        result.assertLogText("Included file: index.xml");
        result.assertLogText(
                "OSGi repository archive was successfully generated for project");
        result.assertLogText("Skipping target definition file generation ");
    }

    @Test
    public void testWhenGenerateP2WithEmbedAndCustomCategory()
            throws Exception {
        File repo = new File("target/test-classes/composite/repository/1.0.0")
                .getCanonicalFile();
        assertThat(repo.exists() && repo.isDirectory()).isTrue();

        MavenRuntime mavenRuntime2 = mavenRuntimeBuilder
                .withCliOptions(
                        "-Dp2ArtifactSetRepositoryUrl=" + repo.toURI().toURL().toExternalForm())
                .build();
        File basedir = resources
                .getBasedir("it-project--p2-with-custom-category");
        MavenExecution exec = mavenRuntime2.forProject(basedir);
        MavenExecutionResult result = exec.execute("clean", "package");

        result.assertErrorFreeLog();
        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test");
//        result.assertLogText(
//                "Resolving p2 artifacts for project osgi-repository.it.test");
        result.assertLogText("Resolved 1 artifact from p2 repositories.");
        result.assertLogText(
                "Finished copying of 1 artifact from p2 repositories.");
        result.assertLogText(
                "Preparing for caching artifacts from maven repositories");
        result.assertLogText(
                "Finished copying of 1 artifact from maven repositories.");
        result.assertLogText("Setting up generation of the OSGi p2 repository");
        result.assertLogText(
                "Started generation of the p2 repository for project");
        result.assertLogText(
                "OSGi p2 repository was generated containing 2 artifacts");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file for project");
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText(
                "Repository index file was successfully generated at");
        result.assertLogText(
                "Setting up generation of the OSGi Repository archive for project");
        result.assertLogText(
                "Starting to pack the items of OSGi repository archive for project");
        result.assertLogText("Included file: plugins/slf4j.simple_1.7.12.jar");
        result.assertLogText("Included file: plugins/aBundle_1.8.4.jar");
        result.assertLogText(
                "Included file: plugins/br.com.c8tech.bundle_1.8.4.jar");
        result.assertLogText("Included file: content.xml");
        result.assertLogText("Included file: index.xml");
        result.assertLogText("Included file: artifacts.xml");
        result.assertLogText(
                "OSGi repository archive was successfully generated for project");
        result.assertLogText(
                "The platform definition file was successfully generated for project ");
    }

    @Test
    public void testWhenGenerateP2WithEmbedAndDefaultCategory()
            throws Exception {
        File repo = new File("target/test-classes/composite/repository/1.0.0")
                .getCanonicalFile();
        assertThat(repo.exists() && repo.isDirectory()).isTrue();

        File ss = new File(
                "target/test-classes/subsystems/aCompositeSubsystem.esa")
                        .getCanonicalFile();
        assertThat(ss.exists() && ss.isFile()).isTrue();

        MavenRuntime mavenRuntime2 = mavenRuntimeBuilder
                .withCliOptions("-DsubsystemPath=" + ss.getAbsolutePath(),
                        "-Dp2ArtifactSetRepositoryUrl=" + repo.toURI().toURL().toExternalForm())
                .build();
        File basedir = resources.getBasedir("it-project--p2");
        MavenExecutionResult result = mavenRuntime2.forProject(basedir)
                .execute("clean", "package");
        result.assertErrorFreeLog();
        result.assertLogText(
                "Setting up downloading of p2 artifacts for project osgi-repository.it.test");
        result.assertLogText("Resolved 1 artifact from p2 repositories.");
        result.assertLogText(
                "Finished copying of 1 artifact from p2 repositories.");
        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test");
        result.assertLogText(
                "Finished copying of 2 artifacts from maven repositories.");
        result.assertLogText("Setting up generation of the OSGi p2 repository");
        result.assertLogText(
                "Started generation of the p2 repository for project");
        result.assertLogText(
                "OSGi p2 repository was generated containing 2 artifacts");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file for project");
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText(
                "Repository index file was successfully generated at");
        result.assertLogText(
                "Setting up generation of the OSGi Repository archive for project");
        result.assertLogText(
                "Starting to pack the items of OSGi repository archive for project");
        result.assertLogText("Included file: plugins/slf4j.simple_1.7.12.jar");
        result.assertLogText("Included file: plugins/aBundle_1.8.4.jar");
        result.assertLogText(
                "Included file: plugins/br.com.c8tech.bundle_1.8.4.jar");
        result.assertLogText("Included file: content.xml");
        result.assertLogText("Included file: index.xml");
        result.assertLogText("Included file: artifacts.xml");
        result.assertLogText(
                "Included file: subsystems/br.com.c8tech.subsystem.composite_0.1.1.qualifier.esa");
        result.assertLogText(
                "OSGi repository archive was successfully generated for project");
        result.assertLogText(
                "Skipping target definition file generation because a P2 repository archive generation was not requested");
    }

    @Test
    public void testWhenGenerateP2WithoutEmbed() throws Exception {
        File repo = new File("target/test-classes/composite/repository/1.0.0")
                .getCanonicalFile();
        assertThat(repo.exists() && repo.isDirectory()).isTrue();

        MavenRuntime mavenRuntime2 = mavenRuntimeBuilder
                .withCliOptions(
                        "-Dp2ArtifactSetRepositoryUrl=" + repo.toURI().toURL().toExternalForm())
                .build();
        File basedir = resources.getBasedir("it-project--p2AndNoEmbed");

        MavenExecutionResult result = mavenRuntime2.forProject(basedir)
                .execute("clean", "package");

        result.assertErrorFreeLog();
        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test");
        result.assertLogText(
                "Finished copying of 1 artifact from maven repositories.");
//        result.assertLogText(
//                "Skipping downloading artifacts from p2 repositories");
        result.assertLogText("Setting up generation of the OSGi p2 repository");
        result.assertLogText(
                "Started generation of the p2 repository for project");
        result.assertLogText("Generating default category definition file");
        result.assertLogText(
                "OSGi p2 repository was generated containing 1 artifact");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file");
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText(
                "Repository index file was successfully generated at");
        result.assertLogText(
                "Setting up generation of the OSGi Repository archive for project");
        result.assertLogText(
                "Starting to pack the items of OSGi repository archive for project");
        result.assertLogText("Included file: plugins/slf4j.simple_1.7.12.jar");
        result.assertLogText("Included file: index.xml");
        result.assertLogText("Included file: content.xml");
        result.assertLogText("Included file: artifacts.xml");
        result.assertLogText(
                "OSGi repository archive was successfully generated for project");
    }

    @Test
    public void testWhenNotEmbeddingAndNotGenerateP2() throws Exception {
        File basedir = resources.getBasedir("it-project--noEmbedAndNoP2");

        MavenExecutionResult result = mavenRuntime.forProject(basedir)
                .execute("clean", "package");

        result.assertErrorFreeLog();
        result.assertLogText(
                "Loading properties from maven artifacts for project");
        result.assertLogText(
                "Nothing to do since any artifact was set to be downloaded");
        result.assertLogText(
                "Setting up downloading of p2 artifacts for project osgi-repository.it.test");
//        result.assertLogText(
//                "Skipping downloading artifacts from p2 repositories");
        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test");
        result.assertLogText(
                "Preparing for caching artifacts from maven repositories");
        result.assertLogText(
                "Finished copying of 1 artifact from maven repositories.");
        result.assertLogText("Setting up generation of the OSGi p2 repository");
        result.assertLogText("Skipping p2 repository generation");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file");
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText(
                "Repository index file was successfully generated at");
        result.assertLogText(
                "Setting up generation of the OSGi Repository archive for project");
        result.assertLogText(
                "Starting to pack the items of OSGi repository archive for project");
        result.assertLogText("Included file: plugins/slf4j-simple-1.7.12.jar");
        result.assertLogText("Included file: index.xml");
        result.assertLogText(
                "OSGi repository archive was successfully generated for project");
    }

    @Test
    public void testInstallDownloadedBundleFromP2() throws Exception {
        File basedir = resources
                .getBasedir("it-project--install-into-local-repo");
        File repo = new File("target/test-classes/composite/repository/1.0.0")
                .getCanonicalFile();
        assertThat(repo.exists() && repo.isDirectory()).isTrue();

        MavenRuntime mavenRuntime2 = mavenRuntimeBuilder
                .withCliOptions(
                        "-Dp2ArtifactSetRepositoryUrl=" + repo.toURI().toURL().toExternalForm())
                .build();
        MavenExecutionResult result = mavenRuntime2.forProject(basedir)
                .execute("clean", "package");
        result.assertErrorFreeLog();
        result.assertLogText(
                "Setting up downloading of p2 artifacts for project osgi-repository.it.test.install");
        result.assertLogText("Resolved 1 artifact from p2 repositories.");
        result.assertLogText(
                "Finished copying of 1 artifact from p2 repositories");
        result.assertLogText(
                "Starting installing artifacts into maven local repository");
        result.assertLogText(
                "Setting up caching of maven artifacts for project osgi-repository.it.test.install");
        result.assertLogText(
                "Preparing for caching artifacts from maven repositories");
        result.assertLogText(
                "Finished copying of 1 artifact from maven repositories.");
        result.assertLogText(
                "Setting up generation of the OSGi p2 repository for project");
        result.assertLogText(
                "Started generation of the p2 repository for project ");
        result.assertLogText(
                "OSGi p2 repository was generated containing 2 artifacts");
        result.assertLogText(
                "Setting up generation of the OSGi repository index file for project osgi-repository.it.test.install");
        result.assertLogText("Started generation of the repository index file");
        result.assertLogText(
                "Repository index file was successfully generated");
        result.assertLogText("Skipping target definition file generation");

    }
}
