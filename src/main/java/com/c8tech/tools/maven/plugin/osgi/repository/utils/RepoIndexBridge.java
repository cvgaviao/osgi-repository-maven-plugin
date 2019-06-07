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
package com.c8tech.tools.maven.plugin.osgi.repository.utils;

/*
 * Part of this code was borrowed from maven-bundle-plugin (https://github.com/apache/felix.git)
 * project that is released under Apache License Version 2.0
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.jdom2.Namespace;
import org.osgi.service.indexer.AnalyzerException;
import org.osgi.service.indexer.ResourceIndexer;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.kalpatec.pojosr.framework.launch.BundleDescriptor;
import de.kalpatec.pojosr.framework.launch.ClasspathScanner;
import de.kalpatec.pojosr.framework.launch.PojoServiceRegistry;
import de.kalpatec.pojosr.framework.launch.PojoServiceRegistryFactory;

/**
 * Wrapper the PojoSr and BIndex.
 *
 * @author Cristiano Gavião
 *
 */
public class RepoIndexBridge {

    /**
     * LOGGER for this plugin.
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(RepoIndexBridge.class);

    public static final Namespace NS = Namespace.getNamespace("repo",
            "http://www.osgi.org/xmlns/repository/v1.0.0");

    public static final String SEARCH_PATTERN = "//repo:resource[repo:capability[repo:attribute[@name='osgi.identity' "
            + "and contains(@value,'%s')] "
            + "and repo:attribute[@name='version' " + "and @value='%s']]]";

    public static final String SEARCH_PATTERN_SNAPSHOT = "//repo:resource[repo:capability[repo:attribute[@name='osgi.identity' "
            + "and contains(@value,'%s')] "
            + "and repo:attribute[@name='version' "
            + "and contains(@value,'%s')]]]";

    /**
     * The ClassLoader to be used by PojoSr.
     */
    private ClassLoader classLoader;

    /**
     * values to be added to PojoSr bundle filter.
     */
    private List<String> extraBundles = new ArrayList<>(
            RepoIndexBridge.defaultExtraBundles());

    /**
     * Used by the BIndex component.
     */
    private File extraKnownBundlesPropertiesFile;

    /**
     * The place where PojoSr/Felix must put cache data.
     */
    private String pojosrOutputDir;

    private final boolean verbose;

    /**
     * Creates a new instance of the bridge class.
     *
     * @param classLoader
     *                                            the classloader to be used.
     * @param extraKnownBundlesPropertiesFile
     *                                            when there are any know bundle
     *                                            property file to pass to
     *                                            indexer library.
     * @param extraBundles
     *                                            when there are extra bundle to
     *                                            be added to PojoSr classpath.
     * @param pojosrOutputDir
     *                                            The output directory.
     * 
     * @param pVerbose
     *                                            Whether log messages should be
     *                                            displayed.
     */
    public RepoIndexBridge(ClassLoader classLoader,
            File extraKnownBundlesPropertiesFile, List<String> extraBundles,
            String pojosrOutputDir, boolean pVerbose) {
        this.classLoader = classLoader;
        this.verbose = pVerbose;
        this.pojosrOutputDir = pojosrOutputDir;
        if (extraBundles != null && !extraBundles.isEmpty()) {
            this.extraBundles.addAll(extraBundles);
        }
        if (extraKnownBundlesPropertiesFile != null) {
            this.extraKnownBundlesPropertiesFile = extraKnownBundlesPropertiesFile;
        }
    }

    protected static List<String> defaultExtraBundles() {
        return Arrays.asList("osgi.core", "org.eclipse.osgi",
                "org.apache.felix.framework", "c8tech.tools.maven.library.osgi.subsystem",
                "c8tech.tools.maven.library.osgi.repoindex",
                "de.kalpatec.pojosr.framework.bare",
                "org.apache.felix.configadmin", "org.apache.felix.scr",
                "org.apache.felix.log");
    }

    private static Properties loadPropertiesFile(File knownBundles)
            throws IOException {
        Properties props = new Properties();
        try (FileInputStream stream = new FileInputStream(knownBundles)) {

            props.load(stream);
        }
        return props;
    }

    private String buildBundleFilter() {
        // only allowed artifacts must be part of execution
        StringBuilder filter = new StringBuilder("(|");
        for (String bsn : extraBundles) {
            filter.append("(Bundle-SymbolicName=");
            filter.append(bsn).append(")");
        }
        filter.append(")");
        return filter.toString();
    }

    /**
     * Generated a new repository index file using the provided set of files.
     * <p>
     * It will use a library based on the OSGi Alliance BIndex in order to
     * create/update the repository index file.
     *
     * @param filesToIndex
     *                            The set of files to index.
     * @param pOutputStream
     *                            The output stream object.
     * @param repoindexConfig
     *                            A map of configuration passed to the indexer
     *                            tool.
     * @throws IllegalArgumentException
     *                                      When the output stream is not valid.
     *
     * @throws IOException
     *                                      if the plugin failed
     * @throws InterruptedException
     *                                      a thread interruption.
     */
    public void generateRepositoryIndex(Set<File> filesToIndex,
            OutputStream pOutputStream, Map<String, String> repoindexConfig)
            throws IOException, InterruptedException {

        if (filesToIndex == null || filesToIndex.isEmpty()) {
            LOGGER.warn("No file was processed by the indexer service.");
            return;
        }

        if (pOutputStream == null) {
            throw new IllegalArgumentException(
                    "The target repository index path informed is not valid.");
        }

        ResourceIndexer index = setupResourceIndexerService();
        try {
            index.index(filesToIndex, pOutputStream, repoindexConfig);
        } catch (AnalyzerException e) {
            throw new IOException(e);
        }
    }

    /**
     *
     * @param artifacts
     *                            A set of artifacts to be processed.
     * @param repoIndexConfig
     *                            A map containing the configuration for the
     *                            indexer.
     * @return A string representing the resource fragment.
     * @throws MojoExecutionException
     *                                    When something goes wrong.
     */
    public String generateResourceFragmentIndexForArtifacts(
            Set<Artifact> artifacts, Map<String, String> repoIndexConfig)
            throws MojoExecutionException {
        // Run
        StringWriter writer = new StringWriter();
        Set<File> filesToIndex = new HashSet<>();

        try {
            for (Artifact artifact2 : artifacts) {
                filesToIndex.add(artifact2.getFile());
            }
            ResourceIndexer index = setupResourceIndexerService();

            index.indexFragment(filesToIndex, writer, repoIndexConfig);
            return writer.toString();
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Repository Indexer was unable to generate an update fragment for artifacts.",
                    e);
        }
    }

    /**
     *
     * @param artifactId
     *                       The artifact ID.
     * @param version
     *                       The artifact version.
     * @return A string representing the pattern.
     */
    public String getArtifactSearchPattern(final String artifactId,
            final String version) {
        String formatted;
        int index = version.indexOf("-SNAPSHOT");
        if (index != -1) {
            formatted = String.format(SEARCH_PATTERN_SNAPSHOT, artifactId,
                    version.substring(0, index));
        } else {
            formatted = String.format(SEARCH_PATTERN, artifactId, version);
        }
        return formatted;
    }

    public boolean isVerbose() {
        return verbose;
    }

    private ResourceIndexer setupResourceIndexerService()
            throws IOException, InterruptedException {

        // Configure PojoSR
        Map<String, Object> pojoSrConfig = new HashMap<>();

        ClasspathScanner scanner = new ClasspathScanner();
        List<BundleDescriptor> bundles = new ArrayList<>();
        try {
            bundles = scanner.scanForBundles(buildBundleFilter(), classLoader);
        } catch (Exception e) {
            throw new IOException(e);
        }
        if (isVerbose()) {
            LOGGER.info(
                    "PojoSr will be loaded using the following classpath: {}",
                    bundles);
            LOGGER.info("PojoSr directory is : {}", pojosrOutputDir);
        }
        pojoSrConfig.put(PojoServiceRegistryFactory.BUNDLE_DESCRIPTORS,
                bundles);
        System.setProperty("org.osgi.framework.storage", pojosrOutputDir);
        System.setProperty("verbose", Boolean.toString(isVerbose()));

        // Start PojoSR Service Registry
        ServiceLoader<PojoServiceRegistryFactory> loader = ServiceLoader
                .load(PojoServiceRegistryFactory.class, classLoader);

        PojoServiceRegistry registry = null;
        ResourceIndexer index;
        try {
            registry = loader.iterator().next()
                    .newPojoServiceRegistry(pojoSrConfig);
        } catch (Exception e) {
            throw new IOException(e);
        }
        // Look for indexer and run index generation
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ServiceTracker tracker = new ServiceTracker(registry.getBundleContext(),
                ResourceIndexer.class.getName(), null);
        tracker.open();
        try {
            index = (ResourceIndexer) tracker.waitForService(5000);
        } catch (InterruptedException e) {
            LOGGER.error("Thread was interrupted !", e);
            // Restore interrupted state...
            throw e;
        }
        if (index == null)
            throw new IOException(
                    "Timed out waiting for ResourceIndexer service.");

        if (extraKnownBundlesPropertiesFile != null) {
            Properties props = loadPropertiesFile(
                    extraKnownBundlesPropertiesFile);
            index.setKnownBundlesExtraProperties(props);
        }

        return index;

    }
}
