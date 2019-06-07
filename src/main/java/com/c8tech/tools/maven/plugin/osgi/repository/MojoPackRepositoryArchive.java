/**
 * ============================================================================
 *  Copyright ©  2015-2019,    Cristiano V. Gavião
 *
 *  All rights reserved.
 *  This program and the accompanying materials are made available under
 *  the terms of the Eclipse Public License v1.0 which accompanies this
 *  distribution and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * ============================================================================
 */
package com.c8tech.tools.maven.plugin.osgi.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;

/**
 * This mojo will generate a Zip archive file for the current project embedding
 * the index.xml and optionally all processed dependency artifacts.
 * <p>
 *
 * It was designed as an optional integrated part of the default lifecycle of
 * the <b>osgi.repository</b> packaging and should not be used alone.
 *
 * @author <a href="cvgaviao@gmail.com">Cristiano Gavião</a>
 */
@Mojo(name = "packIndexedRepositoryArchive", requiresProject = true,
        inheritByDefault = true, aggregator = false, threadSafe = false,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class MojoPackRepositoryArchive extends AbstractOsgiRepositoryMojo {

    @Inject
    private AggregatorBuildContext aggregatorBuildContext;

    /**
     * The Zip archiver.
     */
    @Inject
    @Named("zip")
    private Archiver zipArchiver;

    @Inject
    public MojoPackRepositoryArchive(MavenProject project) {
        super(project);
    }

    @Override
    public void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        getLog().info(
                "Setting up generation of the OSGi Repository archive for project "
                        + getProject().getArtifactId());

        if (isVerbose()) {
            getLog().info("Registering artifacts into the OSGi Repository "
                    + "archive generation incremental build context.");
        }
        File archiveName = calculateRepositoryArchiveName();

        try {
            final Path workdir = getWorkSubDirectory(DEFAULT_WORK_DIR_NAME);

            if (workdir.toFile().listFiles().length == 0) {

                getLog().info("Output directory is empty, bypassing pack... "
                        + getProject().getArtifactId());
            }

            InputSet packInputSet = aggregatorBuildContext.newInputSet();
            packInputSet.addInputs(workdir.toFile(), null, null);
            packInputSet.aggregateIfNecessary(archiveName,
                    (output, inputs) -> generateRepositoryArchive(workdir,
                            output, inputs));
            getProject().getArtifact().setFile(archiveName);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure occurred while generating the OSGi repository archive",
                    e);
        }

    }

    protected void generateRepositoryArchive(Path pWorkDir,
            Output<File> pOutputFile, Iterable<File> pInputs)
            throws IOException {
        getLog().info(
                "Starting to pack the items of OSGi repository archive for project "
                        + getProject().getArtifactId());
        for (File file : pInputs) {
            Path target = pWorkDir.relativize(file.toPath());
            zipArchiver.addFile(file, target.toString());
            if (isVerbose()) {
                getLog().info("  Included file: " + target.toString());
            }
        }
        zipArchiver.setDestFile(pOutputFile.process().getResource());
        zipArchiver.createArchive();
        getLog().info(
                "OSGi repository archive was successfully generated for project "
                        + getProject().getArtifactId());
    }
}
