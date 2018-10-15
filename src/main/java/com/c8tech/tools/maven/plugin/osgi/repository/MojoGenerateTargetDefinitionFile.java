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
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTracker;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;

/**
 * This mojo will generate a target definition file using the generated
 * repository.
 * <p>
 *
 * It was designed as an optional integrated part of the default lifecycle of
 * the <b>osgi.repository</b> packaging and should not be used alone.
 *
 * @author <a href="cvgaviao@gmail.com">Cristiano Gavião</a>
 */
@Mojo(name = "generateTargetDefinitionFile", requiresProject = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        inheritByDefault = true, aggregator = false, threadSafe = true)
public class MojoGenerateTargetDefinitionFile
        extends AbstractOsgiRepositoryMojo {

    @Inject
    private AggregatorBuildContext aggregatorBuildContext;

    private File generatedP2ArchiveName;

    @Parameter(defaultValue = "false")
    private boolean generateTargetPlatformDefinition;

    /**
     * The name used to identify the target definition file.
     */
    @Parameter(defaultValue = "${project.build.finalName}", required = true)
    private String targetDefinitionName;

    @Inject
    public MojoGenerateTargetDefinitionFile(MavenProject pProject) {
        super(pProject);
    }

    private String newUnitLine(ArtifactTracker pArtifactTracker)
            throws IOException {
        String id = pArtifactTracker.getManifestHeaders()
                .get(CommonMojoConstants.OSGI_BUNDLE_HEADER_SN);
        if (id == null || id.isEmpty()) {

            getLog().warn("Ignoring bundle with invalid SN: "
                    + pArtifactTracker.getArtifactId());
            return null;
        }
        int i = id.indexOf(';');
        if (i > 0) {
            id = id.substring(0, i);
        }
        String version = pArtifactTracker.getManifestHeaders()
                .get(CommonMojoConstants.OSGI_BUNDLE_HEADER_VERSION);
        if (version == null || version.isEmpty()) {

            getLog().warn("Ignoring bundle with invalid version: "
                    + pArtifactTracker.getArtifactId());
            return null;
        }

        return "<unit id=\"" + id + "\" version=\"" + version + "\"/>";
    }

    private File calculateTargetDefinitionFileName() {

        String name = getArtifactFileName()
                + (getClassifier() != null ? "-" + getClassifier() : "")
                + ".target";

        return new File(getProject().getBuild().getDirectory(), name);
    }

    @Override
    public void executeMojo()
            throws MojoFailureException, MojoExecutionException {

        getLog().info(
                "Setting up generation of the target definition file for project "
                        + getProject().getArtifactId());

        if (!isGenerateP2() || !generateTargetPlatformDefinition) {
            getLog().warn("Skipping target definition file generation "
                    + "because a P2 repository archive generation was not requested");
            return;
        }
        generatedP2ArchiveName = calculateRepositoryArchiveName();

        if (isGenerateP2() && !generatedP2ArchiveName.exists()) {
            getLog().warn("Skipping target definition file generation "
                    + "because a P2 repository archive was not generated");
            return;
        }

        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(),
                        getWorkSubDirectory(DEFAULT_WORK_DIR_NAME))
                .withGroupingByTypeDirectory(true).withVerbose(isVerbose())
                .withPreviousCachingRequired(true).mavenSetup()
                .withDependenciesHelper(getDependenciesHelper())
                .withRepositorySystem(getRepositorySystem())
                .withCachedFileNamePattern(
                        isGenerateP2() ? CACHED_FILE_PATTERN_DEFAULT_FINALNAME
                                : getCachedFileNamePattern())
                .workspaceSetup().withAssemblyUrlProtocolAllowed(false)
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

        int countp2 = 0;
        if (!getP2ArtifactSets().getP2ArtifactSets().isEmpty()) {
            countp2 = artifactTrackerManager
                    .resolveP2Artifacts(getP2LocalPoolDirectory());
        }

        int countMaven = artifactTrackerManager
                .resolveMavenArtifacts(getScopes());

        if (countp2 + countMaven > 0)
            prepareForTargetDefinitionFileGeneration(artifactTrackerManager);

    }

    protected void generateTargetPlatformDefinitionFile(
            ArtifactTrackerManager pArtifactTrackers, Output<File> pOutputFile,
            Iterable<File> pInputs) throws IOException {
        getLog().info(
                "Start generation of the target definition file for project "
                        + getProject().getArtifactId());

        List<String> xmlFile = new ArrayList<>();
        xmlFile.add(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
        xmlFile.add("<?pde version=\"3.8\"?>");
        xmlFile.add("<target name=\"" + targetDefinitionName
                + "\" sequenceNumber=\"0\">");
        xmlFile.add("<locations>");
        xmlFile.add(
                "<location includeAllPlatforms=\"false\" includeConfigurePhase=\"false\" "
                        + "includeMode=\"slicer\" includeSource=\"false\" type=\"InstallableUnit\">");
        xmlFile.add("<repository location=\"jar:file:"
                + generatedP2ArchiveName.getAbsolutePath() + "!/\"/>");
        for (File file : pInputs) {
            ArtifactTracker artifact = pArtifactTrackers
                    .searchByPath(file.getPath());
            if (artifact != null && "jar".equals(artifact.getType())) {

                String piece = newUnitLine(artifact);
                if (piece != null && !piece.isEmpty()) {
                    xmlFile.add(piece);
                    if (isVerbose()) {
                        getLog().info("  Included unit for: "
                                + artifact.getArtifactId());
                    }
                }
            } else {
                continue;
            }
        }
        xmlFile.add("</location>");
        xmlFile.add("</locations>");
        xmlFile.add("</target>");

        Files.write(pOutputFile.getResource().toPath(), xmlFile,
                StandardCharsets.UTF_8);

        getLog().info(
                "The platform definition file was successfully generated for project "
                        + getProject().getArtifactId());
    }

    private void prepareForTargetDefinitionFileGeneration(
            final ArtifactTrackerManager pArtifactTrackerManager)
            throws MojoExecutionException {
        if (isVerbose()) {
            getLog().info("Registering artifacts into the OSGi Repository "
                    + "archive generation incremental build context.");
        }
        InputSet inputSet = registerArtifactsIntoAggregatorBuildContext(
                pArtifactTrackerManager.lookupEmbeddableArtifactTrackers(),
                aggregatorBuildContext, true);

        try {
            inputSet.addInput(generatedP2ArchiveName);
            inputSet.aggregateIfNecessary(calculateTargetDefinitionFileName(),
                    (output, inputs) -> generateTargetPlatformDefinitionFile(
                            pArtifactTrackerManager, output, inputs));
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure occurred while generating the OSGi repository archive",
                    e);
        }

    }
}
