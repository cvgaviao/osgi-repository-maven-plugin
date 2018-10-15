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
package br.com.c8tech.tools.maven.plugin.osgi.repository.ut;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;

import java.io.File;
import java.net.URL;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DownloadDependenciesUnitTest extends AbstractOsgiRepositoryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testUseCacheWhenOfflineWhileDownloadingFromP2()
            throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));
        incrementalBuildRule.executeMojo(project, "downloadP2Artifacts",
                newParameter("verbose", "true"));
    }

    // @Test
    public void testCopyFilesFromMavenToCache() throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "subsystems/aCompositeSubsystem.esa", "1.0",
                true, Artifact.SCOPE_COMPILE, "osgi.subsystem.composite",
                false);
        // intentionally added with wrong type
        addDependency(project, "jars/01-bsn+version.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "osgi.subsystem.composite", true);
        addDependency(project, "jars/anotherBundle.jar", "1.0", true,
                Artifact.SCOPE_PROVIDED, "jar", false);
        addDependency(project, "jars/aNonValidBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aTransitiveDependencyBundle.jar", "1.0",
                false, Artifact.SCOPE_COMPILE, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("scopes", "compile,provided"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target/cache"),
                "plugins/anotherBundle-1.0.0.jar",
                "subsystems/aCompositeSubsystem-0.1.1.qualifier.esa");

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("scopes", "compile,provided"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertCarriedOverOutputs(
                new File(project.getBasedir(), "target/cache"),
                "plugins/anotherBundle-1.0.0.jar",
                "subsystems/aCompositeSubsystem-0.1.1.qualifier.esa");

    }

    @Test
    public void testCopyFilesFromP2ToCache() throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));
        URL repository = testProperties.getClass()
                .getResource("/composite/repository/1.0.0");
        // Path localrepo = Paths.get(project.getBasedir().getAbsolutePath(),
        // "target/m2");
        incrementalBuildRule.executeMojo(project, "downloadP2Artifacts",
                // newParameter("localRepositoryPath",
                // localrepo.toString()),
                // newParameter("installOnLocalRepository", "true"),
                newParameter("verbose", "true"),
                newParameterP2ArtifactSets(
                        newParameterP2ArtifactSet(repository.toExternalForm(),
                                "group", newArtifactList("aBundle:1.8.4"))));
        // Path repo = project.getBasedir().toPath().resolve("target/repo");
        // assertTrue("Maven Repository file was not created.",
        // repo.toFile().exists());

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target/cache"),
                "plugins/aBundle_1.8.4.jar");

        incrementalBuildRule.executeMojo(project, "downloadP2Artifacts",
                newParameter("verbose", "true"),
                newParameterP2ArtifactSets(
                        newParameterP2ArtifactSet(repository.toExternalForm(),
                                "group", newArtifactList("aBundle:1.8.4"))));
        incrementalBuildRule.assertCarriedOverOutputs(
                new File(project.getBasedir(), "target/cache"),
                "plugins/aBundle_1.8.4.jar");
    }


    @Test(expected = MojoExecutionException.class)
    public void testWrongPackagingFailure() throws Exception {
        File basedir = testResources
                .getBasedir("ut-project--fail-wrong-packaging");
        incrementalBuildRule.executeMojo(basedir,
                "generateIndexFromDependencies");

    }
}
