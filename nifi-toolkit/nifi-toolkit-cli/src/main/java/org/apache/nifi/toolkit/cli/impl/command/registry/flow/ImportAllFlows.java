/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.toolkit.cli.impl.command.registry.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.nifi.flow.VersionedFlowCoordinates;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.registry.bucket.Bucket;
import org.apache.nifi.registry.client.BucketClient;
import org.apache.nifi.registry.client.FlowClient;
import org.apache.nifi.registry.client.FlowSnapshotClient;
import org.apache.nifi.registry.client.NiFiRegistryClient;
import org.apache.nifi.registry.client.NiFiRegistryException;
import org.apache.nifi.registry.flow.VersionedFlow;
import org.apache.nifi.registry.flow.VersionedFlowSnapshot;
import org.apache.nifi.registry.flow.VersionedFlowSnapshotMetadata;
import org.apache.nifi.toolkit.cli.api.CommandException;
import org.apache.nifi.toolkit.cli.api.Context;
import org.apache.nifi.toolkit.cli.impl.command.CommandOption;
import org.apache.nifi.toolkit.cli.impl.command.registry.AbstractNiFiRegistryCommand;
import org.apache.nifi.toolkit.cli.impl.command.registry.bucket.ListBuckets;
import org.apache.nifi.toolkit.cli.impl.result.StringResult;
import org.apache.nifi.toolkit.cli.impl.util.JacksonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImportAllFlows extends AbstractNiFiRegistryCommand<StringResult> {
    private static final String FILE_NAME_PREFIX = "toolkit_registry_export_all_";
    private static final String SKIPPING_BUCKET_CREATION = " already exists, skipping bucket creation...";
    private static final String SKIPPING_IMPORT = " already exists, skipping import...";
    private static final String SKIPPING_FLOW_CREATION = " already exists, skipping flow creation...";
    private static final String IMPORT_COMPLETED = "Import completed...";
    private static final String ALL_BUCKETS_COLLECTED = "All buckets collected...";
    private static final String ALL_FLOWS_COLLECTED = "All flows collected...";
    private static final String ALL_FLOW_VERSIONS_COLLECTED = "All flow versions collected...";
    private static final String FILE_NAME_SEPARATOR = "_";
    private static final String STORAGE_LOCATION_URL = "%s/nifi-registry-api/buckets/%s/flows/%s/versions/%s";
    private static final String VERSION_IMPORTING_STARTED = "Importing %s - %s to %s";
    private static final String VERSION_IMPORTING_FINISHED = "Successfully imported %s - %s to %s";
    private static final ObjectMapper MAPPER = JacksonUtils.getObjectMapper();
    private final ListBuckets listBuckets;
    private final ListFlows listFlows;
    private final ListFlowVersions listFlowVersions;

    public ImportAllFlows() {
        super("import-all-flows", StringResult.class);
        this.listBuckets = new ListBuckets();
        this.listFlows = new ListFlows();
        this.listFlowVersions = new ListFlowVersions();
    }

    @Override
    protected void doInitialize(Context context) {
        addOption(CommandOption.INPUT_SOURCE.createOption());
        addOption(CommandOption.SKIP_EXISTING.createOption());

        listBuckets.initialize(context);
        listFlows.initialize(context);
        listFlowVersions.initialize(context);
    }

    @Override
    public String getDescription() {
        return "From a provided directory as input, the directory content must be generated by the export-all-flows command, " +
                "based on the file contents, the corresponding buckets, flows and flow versions will be created." +
                "If not configured otherwise, already existing objects will be skipped.";
    }

    @Override
    public StringResult doExecute(final NiFiRegistryClient client, final Properties properties) throws IOException, NiFiRegistryException, ParseException, CommandException {
        final boolean skip = getArg(properties, CommandOption.SKIP_EXISTING) == null ? Boolean.FALSE : Boolean.TRUE;
        final boolean isInteractive = getContext().isInteractive();

        //Gather all buckets and create a map for easier search by bucket name
        final Map<String, String> bucketMap = getBucketMap(client, isInteractive);

        // Gather all flows and create a map for easier search by flow name.
        // As flow name is only unique within the same bucket we need to use the bucket id in the key as well
        final Map<Pair<String, String>, String> flowMap = getFlowMap(client, bucketMap, isInteractive);
        final Map<Pair<String, String>, String> flowCreated = new HashMap<>();

        // Gather all flow versions and create a map for easier search by flow id
        final Map<String, List<Integer>> versionMap = getVersionMap(client, flowMap, isInteractive);

        // Create file path list
        final List<VersionFileMetaData> files = getFilePathList(properties);

        // As we need to keep the version order the list needs to be sorted
        files.sort(Comparator.comparing(VersionFileMetaData::getBucketName)
                .thenComparing(VersionFileMetaData::getFlowName)
                .thenComparing(VersionFileMetaData::getVersion));

        for (VersionFileMetaData file : files) {
            final String inputSource = file.getInputSource();
            final String fileContent = getInputSourceContent(inputSource);
            final VersionedFlowSnapshot snapshot = MAPPER.readValue(fileContent, VersionedFlowSnapshot.class);

            final String bucketName = snapshot.getBucket().getName();
            final String bucketDescription = snapshot.getBucket().getDescription();
            final String flowName = snapshot.getFlow().getName();
            final String flowDescription = snapshot.getFlow().getDescription();
            final int flowVersion = snapshot.getSnapshotMetadata().getVersion();
            // The original bucket and flow ids must be kept otherwise NiFi won't be able to synchronize with the NiFi Registry
            final String flowId = snapshot.getFlow().getIdentifier();
            final String bucketId = snapshot.getBucket().getIdentifier();

            printMessage(isInteractive, String.format(VERSION_IMPORTING_STARTED, flowName, flowVersion, bucketName));

            // Create bucket if missing
            if (bucketMap.containsKey(bucketName)) {
                printMessage(isInteractive, bucketName + SKIPPING_BUCKET_CREATION);
            } else {
                //The original bucket id must be kept otherwise NiFi won't be able to synchronize with the NiFi Registry
                createBucket(client, bucketMap, bucketName, bucketDescription, bucketId);
            }

            // Create flow if missing
            if (flowMap.containsKey(new ImmutablePair<>(bucketId, flowName))) {
                if (skip) {
                    printMessage(isInteractive, flowName + SKIPPING_IMPORT);
                    continue;
                } else {
                    //flowId
                    printMessage(isInteractive, flowName + SKIPPING_FLOW_CREATION);
                }
            } else if (!flowCreated.containsKey(new ImmutablePair<>(bucketId, flowName))) {
                createFlow(client, flowCreated, flowId, flowName, flowDescription, bucketId);
            }

            // Create missing flow versions
            if (!versionMap.getOrDefault(flowId, Collections.emptyList()).contains(flowVersion)) {
                //update storage location
                final String registryUrl = getRequiredArg(properties, CommandOption.URL);

                updateStorageLocation(snapshot.getFlowContents(), registryUrl);

                createFlowVersion(client, snapshot, bucketId, flowId);
            }

            printMessage(isInteractive, String.format(VERSION_IMPORTING_FINISHED, flowName, flowVersion, bucketName));
        }
        return new StringResult(IMPORT_COMPLETED, getContext().isInteractive());
    }

    private Map<String, String> getBucketMap(final NiFiRegistryClient client, final boolean isInteractive) throws IOException, NiFiRegistryException {
        printMessage(isInteractive, ALL_BUCKETS_COLLECTED);

        return listBuckets.doExecute(client, new Properties())
                .getResult()
                .stream()
                .collect(Collectors.toMap(Bucket::getName, Bucket::getIdentifier));
    }

    private Map<Pair<String, String>, String> getFlowMap(final NiFiRegistryClient client, final Map<String, String> bucketMap,
                                                         final boolean isInteractive) throws ParseException, IOException, NiFiRegistryException {
        printMessage(isInteractive, ALL_FLOWS_COLLECTED);
        return getVersionedFlows(client, bucketMap)
                .stream()
                .collect(Collectors.toMap(e -> new ImmutablePair<>(e.getBucketIdentifier(), e.getName()),
                        VersionedFlow::getIdentifier));
    }

    private List<VersionedFlow> getVersionedFlows(final NiFiRegistryClient client, final Map<String, String> bucketMap) throws ParseException, IOException, NiFiRegistryException {
        final List<VersionedFlow> flows = new ArrayList<>();
        for (final String id : bucketMap.values()) {
            final Properties flowProperties = new Properties();
            flowProperties.setProperty(CommandOption.BUCKET_ID.getLongName(), id);

            flows.addAll(listFlows.doExecute(client, flowProperties).getResult());
        }
        return flows;
    }

    private Map<String, List<Integer>> getVersionMap(final NiFiRegistryClient client, final Map<Pair<String, String>, String> flowMap,
                                                     final  boolean isInteractive) throws ParseException, IOException, NiFiRegistryException {
        printMessage(isInteractive, ALL_FLOW_VERSIONS_COLLECTED);
        return getVersionedFlowSnapshotMetadataList(client, flowMap)
                .stream()
                .collect(Collectors.groupingBy(VersionedFlowSnapshotMetadata::getFlowIdentifier,
                        Collectors.mapping(VersionedFlowSnapshotMetadata::getVersion, Collectors.toList())));
    }

    private List<VersionedFlowSnapshotMetadata> getVersionedFlowSnapshotMetadataList(final NiFiRegistryClient client,
                                                                                     final Map<Pair<String, String>, String> flowMap) throws ParseException, IOException, NiFiRegistryException {
        final List<VersionedFlowSnapshotMetadata> versions = new ArrayList<>();
        for (final String flowIds : flowMap.values()) {
            final Properties flowVersionProperties = new Properties();
            flowVersionProperties.setProperty(CommandOption.FLOW_ID.getLongName(), flowIds);

            versions.addAll(listFlowVersions.doExecute(client, flowVersionProperties).getResult());
        }
        return versions;
    }

    private List<VersionFileMetaData> getFilePathList(final Properties properties) throws MissingOptionException, NiFiRegistryException {
        final String directory = getRequiredArg(properties, CommandOption.INPUT_SOURCE);
        final List<VersionFileMetaData> files;

        try (final Stream<Path> paths = Files.list(Paths.get(directory))) {
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(FILE_NAME_PREFIX))
                    .map(VersionFileMetaData::new)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new NiFiRegistryException("File listing failed", e);
        }
        return files;
    }

    private void createBucket(final NiFiRegistryClient client, final Map<String, String> bucketMap, final String bucketName,
                                final String bucketDescription, final String bucketId) throws IOException, NiFiRegistryException {
        final BucketClient bucketClient = client.getBucketClient();
        final Bucket bucket = new Bucket();
        bucket.setIdentifier(bucketId);
        bucket.setName(bucketName);
        bucket.setDescription(bucketDescription);

        bucketClient.create(bucket, Boolean.TRUE);
        bucketMap.put(bucketName, bucketId);
    }

    private void createFlow(final NiFiRegistryClient client, final Map<Pair<String, String>, String> flowCreated,
                            final String flowId, final String flowName, final String flowDescription, final String bucketId) throws IOException, NiFiRegistryException {
        final FlowClient flowClient = client.getFlowClient();
        final VersionedFlow flow = new VersionedFlow();
        flow.setIdentifier(flowId);
        flow.setName(flowName);
        flow.setDescription(flowDescription);
        flow.setBucketIdentifier(bucketId);

        flowClient.create(flow);
        flowCreated.put(new ImmutablePair<>(bucketId, flowName), flowId);
    }

    private void updateStorageLocation(final VersionedProcessGroup group, final String registryUrl) {
        final  VersionedFlowCoordinates flowCoordinates = group.getVersionedFlowCoordinates();
        if (flowCoordinates != null &&  !Strings.CS.startsWith(flowCoordinates.getStorageLocation(), registryUrl)) {
            final String updatedStorageLocation = String.format(STORAGE_LOCATION_URL, registryUrl, flowCoordinates.getBucketId(),
                    flowCoordinates.getFlowId(), flowCoordinates.getVersion());

            flowCoordinates.setStorageLocation(updatedStorageLocation);
        }

        for (VersionedProcessGroup processGroup : group.getProcessGroups()) {
            updateStorageLocation(processGroup, registryUrl);
        }
    }

    private void createFlowVersion(final NiFiRegistryClient client, final VersionedFlowSnapshot snapshot, final String bucketId, final String flowId) throws IOException, NiFiRegistryException {
        final FlowSnapshotClient snapshotClient = client.getFlowSnapshotClient();

        int version;
        try {
            final VersionedFlowSnapshotMetadata latestMetadata = snapshotClient.getLatestMetadata(bucketId, flowId);
            version = latestMetadata.getVersion() + 1;
        } catch (NiFiRegistryException e) {
            // when there are no versions it produces a 404 not found
            version = 1;
        }
        snapshot.getSnapshotMetadata().setFlowIdentifier(flowId);
        snapshot.getSnapshotMetadata().setBucketIdentifier(bucketId);
        snapshot.getSnapshotMetadata().setVersion(version);
        snapshotClient.create(snapshot, true);
    }

    private void printMessage(final boolean isInteractive, final String message) {
        if (isInteractive) {
            println();
            println(message);
            println();
        }
    }

    public static class VersionFileMetaData {
        private final String inputSource;
        private final String bucketName;
        private final String flowName;
        private final int version;

        public VersionFileMetaData(final Path path) {
            final String[] fileNameElements = path.getFileName().toString().split(FILE_NAME_SEPARATOR);
            this.inputSource = path.toString();
            this.bucketName = fileNameElements[4];
            this.flowName = fileNameElements[5];
            this.version = Integer.parseInt(fileNameElements[6]);
        }

        public String getInputSource() {
            return inputSource;
        }

        public String getBucketName() {
            return bucketName;
        }

        public String getFlowName() {
            return flowName;
        }

        public int getVersion() {
            return version;
        }
    }
}
