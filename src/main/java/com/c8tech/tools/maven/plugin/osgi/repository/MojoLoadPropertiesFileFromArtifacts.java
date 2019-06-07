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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.PropertiesArtifactSet;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTracker;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import io.takari.incrementalbuild.BuildContext;

/**
 * This mojo will download the declared property artifact in the configuration
 * tag <b>artifactConfigSets</b> and also load its values into the maven
 * reactor, so they can be used by subsequent mojo executions.
 * <p>
 * As the org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher
 * application was designed to work with eclipse generated bundles which uses
 * its symbolic-name and version is separated by "_" instead the one used by
 * maven "-", it will be necessary to rename the downloaded artifact file name.
 * <p>
 * It was not designed be used stand alone, but as an integrated part of the
 * default lifecycle of the <b>osgi.repository</b> packaging type.
 *
 * @author Cristiano Gavião
 *
 */
@Mojo(name = "loadProperties", defaultPhase = LifecyclePhase.VALIDATE,
        threadSafe = false,
        requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM,
        requiresProject = true, inheritByDefault = true, aggregator = false)
public class MojoLoadPropertiesFileFromArtifacts
        extends AbstractOsgiRepositoryMojo {

    /**
     * This parameter ensures that the artifact will be cached from remote
     * repository but its values won't be loaded into the maven reactor.
     */
    @Parameter(defaultValue = "false")
    private boolean cacheOnly;

    private final BuildContext copyBuildContext;

    /**
     * Prefix that will be added before name of each property. Can be useful for
     * separating properties with same name from different files.
     */
    @Parameter
    private String keyPrefix = null;

    @Parameter
    private PropertiesArtifactSet propertiesArtifactSet;

    @Inject
    public MojoLoadPropertiesFileFromArtifacts(MavenProject project,
            BuildContext pCopyBuildContext) {
        super(project);
        copyBuildContext = pCopyBuildContext;
        addExtraSupportedPackaging("osgi.repository");
    }

    @Override
    protected void doBeforeSkipMojo() throws MojoExecutionException {
        // do nothing

    }

    @Override
    protected void executeExtraInitializationSteps()
            throws MojoExecutionException {
        // do nothing

    }

    @Override
    protected void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        getLog().info("Loading properties from maven artifacts for project "
                + getProject().getArtifactId());
        if (propertiesArtifactSet == null || propertiesArtifactSet.isEmpty()) {
            getLog().info(
                    "    Nothing to do since any artifact was set to be downloaded...");
            return;
        }
        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(), getCacheDirectory())
                .withGroupingByTypeDirectory(true).withVerbose(isVerbose())
                .withPreviousCachingRequired(false).mavenSetup()
                .withDependenciesHelper(getDependenciesHelper())
                .withRepositorySystem(getRepositorySystem()).workspaceSetup()
                .withAssemblyUrlProtocolAllowed(isWorkspaceResolutionAllowed())
                .withPackOnTheFlyAllowed(isWorkspaceResolutionAllowed())
                .endWorkspaceSetup().mavenFiltering()
                .withArtifactFilter(getRepositoryValidArtifactFilter())
                .withPropertiesArtifactSet(propertiesArtifactSet)
                .endMavenFiltering().endMavenSetup().build();

        if (artifactTrackerManager.resolvePropertiesArtifactSet() == 0) {
            return;
        }

        artifactTrackerManager.copyMavenArtifactsToCache(copyBuildContext);

        if (cacheOnly) {
            return;
        }

        for (ArtifactTracker artifactTracker : artifactTrackerManager
                .getPropertiesArtifactTrackers()) {

            try {
                loadProperties(artifactTracker);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Error reading properties for project.", e);
            }
        }
    }

    private void loadProperties(ArtifactTracker pArtifactTracker)
            throws IOException {
        getLog().info("    Loading properties from "
                + pArtifactTracker.getCachedFilePath().toFile());

        try (final InputStream stream = pArtifactTracker.getCachedFilePath()
                .toFile().toURI().toURL().openStream();) {

            if (keyPrefix != null) {
                Properties properties = new Properties();
                properties.load(stream);
                Properties projectProperties = getProject().getProperties();
                for (String key : properties.stringPropertyNames()) {
                    projectProperties.put(keyPrefix + key, properties.get(key));
                }
            } else {
                getProject().getProperties().load(stream);
            }
        }
    }
}
