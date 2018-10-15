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
package com.c8tech.tools.maven.plugin.osgi.repository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.sisu.equinox.launching.internal.P2ApplicationLauncher;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTracker;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import io.takari.incrementalbuild.BasicBuildContext;

/**
 * This mojo will generate an Eclipse P2 repository using as source the declared
 * dependencies.
 * <p>
 * It was designed as an optional integrated part of the default lifecycle of
 * the <b>osgi.repository</b> packaging and should not be used alone. packaging
 *
 * @author Cristiano Gavião
 *
 */
@Mojo(name = "generateP2FromDependencies", threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresProject = true, inheritByDefault = true, aggregator = false)
public class MojoGenerateP2FromDependencies extends AbstractOsgiRepositoryMojo {

    private static final String APPLICATION_CATEGORIES_PUBLISHER = "org.eclipse.equinox.p2.publisher.CategoryPublisher";

    private static final String APPLICATION_CONTENT_PUBLISHER = "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher";

    private static final String CATEGORY_FILE_NAME = "category.xml";

    private static final String FILE_SCHEME = "file://";

    @Inject
    private BasicBuildContext buildContext;

    /**
     * The default localization of a p2 custom category definition xml file.
     * <p>
     * It is set by default to ${osgi.repository.cacheDirectory}/category.xml.
     */
    @Parameter(property = "osgi.repository.categoryDefinitionFile")
    private File categoryDefinitionFile;

    @Inject
    private P2ApplicationLauncher launcher;

    @Parameter(defaultValue = "0",
            property = "osgi.repository.timeoutInSeconds")
    private int timeoutInSeconds;

    @Inject
    public MojoGenerateP2FromDependencies(final MavenProject project) {
        super(project);
    }

    private void buildDefaultCategoryDefinitionFile()
            throws MojoFailureException, MojoExecutionException {
        if (categoryDefinitionFile == null) {
            throw new MojoFailureException(
                    "The category definition file location is not valid");
        }
        List<String> builder = new ArrayList<>();
        builder.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        builder.add("<site>");
        builder.add("<category-def name=\"" + getProject().getArtifactId()
                + "\" label=\"" + getProject().getName() + "\"/>");
        builder.add("<iu>");
        builder.add(
                "<category name=\"" + getProject().getArtifactId() + "\"/>");
        builder.add("<query>");
        builder.add(
                "<expression type=\"match\">providedCapabilities.exists(p | p.namespace == 'osgi.bundle')</expression>");
        builder.add("</query>");
        builder.add("</iu>");
        builder.add("</site>");
        try {
            Files.write(categoryDefinitionFile.toPath(), builder,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "An error occurred while generating the default category definition file.",
                    e);
        }
    }

    @Override
    protected void executeMojo() // NOSONAR
            throws MojoExecutionException, MojoFailureException {

        getLog().info(
                "Setting up generation of the OSGi p2 repository for project "
                        + getProject().getArtifactId());

        if (!isGenerateP2()) {
            getLog().info(
                    "Skipping p2 repository generation since it is not generateP2 parameter is false.");
            return;
        }

        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(), getCacheDirectory())
                .withGroupingByTypeDirectory(true).withVerbose(isVerbose())
                .withPreviousCachingRequired(true).mavenSetup()
                .withDependenciesHelper(getDependenciesHelper())
                .withCachedFileNamePattern(
                        isGenerateP2() ? CACHED_FILE_PATTERN_DEFAULT_FINALNAME
                                : getCachedFileNamePattern())
                .withRepositorySystem(getRepositorySystem()).workspaceSetup()
                .withAssemblyUrlProtocolAllowed(false)
                .withPackOnTheFlyAllowed(true).endWorkspaceSetup()
                .mavenFiltering()
                .withArtifactFilter(getRepositoryValidArtifactFilter())
                .withOptional(isOptionalConsidered())
                .withTransitive(isTransitiveConsidered())
                .withScopes(getScopes())
                .withMavenArtifactSet(getMavenArtifactSet())
                .withExcludedDependencies(getExcludedArtifacts())
                .endMavenFiltering().endMavenSetup().p2Setup()
                .withDefaultGroupId(getDefaultGroupId())
                .withP2ArtifactSets(getP2ArtifactSets()).endP2Setup().build();

        if (!getP2ArtifactSets().getP2ArtifactSets().isEmpty()) {
            artifactTrackerManager
                    .resolveP2Artifacts(getP2LocalPoolDirectory());
        }

        artifactTrackerManager.resolveMavenArtifacts(getScopes());

        if (artifactTrackerManager.getAllArtifactTrackers().isEmpty()) {
            getLog().info(
                    "Skipping p2 repository generation since there are no artifacts to process.");
            return;
        }
        getLog().info("Started generation of the p2 repository for project "
                + getProject().getArtifactId());

        Set<ArtifactTracker> allArtifacts = artifactTrackerManager
                .getAllArtifactTrackers();
        int count = 0;
        for (ArtifactTracker input : allArtifacts) {
            if (input.isCached()) {
                buildContext.registerInput(input.getCachedFilePath().toFile());
                count++;
            }
        }
        if (count == 0) {
            throw new MojoExecutionException(
                    "Aborting execution. No artifact was cached.");
        }

        if (buildContext.isProcessingRequired()) {

            Path outputDir = getWorkSubDirectory(DEFAULT_WORK_DIR_NAME);

            publishP2Content(outputDir);

            publishP2Category(outputDir);

            File[] outputFiles = outputDir
                    .resolve(CommonMojoConstants.OSGI_BUNDLES_DIRECTORY)
                    .toFile().listFiles();
            for (File outputFile : outputFiles) {
                buildContext.processOutput(outputFile);
            }
            count = outputFiles.length;
            getLog().info("OSGi p2 repository was generated containing "
                    + CommonMojoConstants.MSG_CHOICE_ARTIFACT
                            .format(new Object[] { count }));
        }
    }

    private void publishP2Category(Path outputDir)
            throws MojoFailureException, MojoExecutionException {
        if (!categoryDefinitionFile.exists()
                || !categoryDefinitionFile.canRead()) {
            getLog().info("Generating default category definition file");
            buildDefaultCategoryDefinitionFile();
        } else {
            getLog().info(String.format(
                    "Using default category definition file from %s.",
                    categoryDefinitionFile.getAbsolutePath()));
        }
        launcher.setWorkingDirectory(outputDir.toAbsolutePath().toFile());
        launcher.setApplicationName(APPLICATION_CATEGORIES_PUBLISHER);
        launcher.addArguments("-metadataRepository",
                FILE_SCHEME + outputDir.toAbsolutePath().toString());
        launcher.addArguments("-categoryDefinition",
                FILE_SCHEME + categoryDefinitionFile.getAbsolutePath());

        int result = launcher.execute(timeoutInSeconds);
        if (result != 0) {
            throw new MojoFailureException(
                    "P2 publisher return code was " + result);
        }
    }

    private void publishP2Content(Path outputDir)
            throws MojoExecutionException {

        launcher.setWorkingDirectory(outputDir.toAbsolutePath().toFile());
        launcher.setApplicationName(APPLICATION_CONTENT_PUBLISHER);
        launcher.addArguments("-artifactRepository",
                FILE_SCHEME + outputDir.toAbsolutePath().toString());
        launcher.addArguments("-metadataRepository",
                FILE_SCHEME + outputDir.toAbsolutePath().toString());
        launcher.addArguments("-publishArtifacts");
        launcher.addArguments("-artifactRepositoryName",
                getProject().getArtifactId());
        launcher.addArguments("-metadataRepositoryName",
                getProject().getArtifactId());
        launcher.addArguments("-source",
                getCacheDirectory().toAbsolutePath().toString());

        int result = launcher.execute(timeoutInSeconds);
        if (result != 0) {
            throw new MojoExecutionException(
                    "P2 publisher return code was " + result);
        }
    }

    @Override
    public void setCacheDirectory(File cacheDirectory) {
        super.setCacheDirectory(cacheDirectory);
        categoryDefinitionFile = new File(getCacheDirectory().toFile(),
                CATEGORY_FILE_NAME);
    }
}
