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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.5.0" })
public class OfflineBeahavioursIntegrationTest {

    public MavenRuntime mavenRuntime;

    public final MavenRuntimeBuilder mavenRuntimeBuilder;

    public OfflineBeahavioursIntegrationTest(
            MavenRuntimeBuilder verifierBuilder) throws Exception {
        this.mavenRuntimeBuilder = verifierBuilder;

        this.mavenRuntime = verifierBuilder.withCliOptions("-B", "-o").build();
    }

    @Rule
    public final TestResources resources = new TestResources();

    @Test
    public void testDownloadingBundlesFromP2WithOfflineMode() throws Exception {
        File repo = new File("target/test-classes/composite/repository/1.0.0")
                .getCanonicalFile();
        assertThat(repo.exists() && repo.isDirectory()).isTrue();

        MavenRuntime mavenRuntime2 = mavenRuntimeBuilder
                .withCliOptions(
                        "-Drepodir=" + repo.toURI().toURL().toExternalForm())
                .build();
        File basedir = resources.getBasedir("it-project--p2-offline");
        Path plugins = Files.createDirectories(
                basedir.toPath().resolve("target/cache/plugins"));

        URL fileUrl = getClass().getResource("/jars/aBundle.jar");
        if (fileUrl == null) {
            throw new IllegalArgumentException(
                    "A dependency file was not found at:" + fileUrl);
        }
        File sourceFile = new File(fileUrl.getFile());
        Files.copy(sourceFile.toPath(), plugins.resolve("aBundle_1.8.4.jar"));

        MavenExecutionResult result = mavenRuntime2.forProject(basedir)
                .execute("package");

        result.assertErrorFreeLog();
    }

//    @Test
//    public void testFailureFindingCacheDirWithOfflineMode() throws Exception {
//        File basedir = resources.getBasedir("it-project--p2-offline");
//        Path plugins = Files.createDirectories(
//                basedir.toPath().resolve("target/cache/plugins"));
//
//        MavenExecutionResult result = mavenRuntime.forProject(basedir)
//                .execute("package");
//
//        result.assertLogText("Failure to find cache directory.");
//    }

}
