/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.build

import java.io.Serializable
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

/**
 * Loads Library groups and versions from a specified TOML file.
 */
abstract class LibraryVersionsService : BuildService<LibraryVersionsService.Parameters> {

    interface Parameters : BuildServiceParameters {
        var tomlFileName: String
        var tomlFileContents: Provider<String>
        var composeCustomVersion: Provider<String>
        var composeCustomGroup: Provider<String>
        var useMultiplatformGroupVersions: Provider<Boolean>
        var libsOverrideVersions: Provider<Map<String, String>>
    }

    private val parsedTomlFile: TomlParseResult by lazy {
        val result = Toml.parse(parameters.tomlFileContents.get())
        if (result.hasErrors()) {
            val issues = result.errors().map {
                "${parameters.tomlFileName}:${it.position()}: ${it.message}"
            }.joinToString(separator = "\n")
            throw Exception("${parameters.tomlFileName} file has issues.\n$issues")
        }
        result
    }

    val useMultiplatformGroupVersions
        get() = parameters.useMultiplatformGroupVersions.get()

    private fun getTable(key: String): TomlTable {
        return parsedTomlFile.getTable(key)
            ?: throw GradleException("Library versions toml file is missing [$key] table")
    }

    // map from name of constant to Version
    val libraryVersions: Map<String, Version> by lazy {
        val versions = getTable("versions")
        val libsGroupsAndVersions = parameters.libsOverrideVersions.get()
        versions.keySet().associateWith { versionName ->
            val tagName = libsGroupsAndVersions.keys.firstOrNull { versionName == it }
            val versionForTag = libsGroupsAndVersions[tagName]
            val versionValue =
                if (versionName.startsWith("COMPOSE") &&
                    parameters.composeCustomVersion.isPresent
                ) {
                    parameters.composeCustomVersion.get()
                } else if (tagName != null && versionForTag != null) {
                    versionForTag
                } else {
                    // Do not use version from toml to about accidentally publish "stable" version
                    //
                    // We use a big version, so it will win in case of version conflict during
                    // local runs:
                    // project("compose:ui") -> lifecycle-runtime-compose:2.8.4 -> compose.runtime:runtime:1.6.11
                    // project("compose:ui") -> project("compose:runtime")
                    // project("compose:runtime") should override compose.runtime:runtime:1.6.11 by default
                    "9999.0.0-SNAPSHOT"
                }
            Version.parseOrNull(versionValue)
                ?: throw GradleException(
                    "$versionName does not match expected format - $versionValue"
                )
        }
    }

    // map of library groups keyed by their variable name in the toml file
    val libraryGroups: Map<String, LibraryGroup> by lazy {
        val result = mutableMapOf<String, LibraryGroup>()
        for (association in libraryGroupAssociations) {
            result.put(association.declarationName, association.libraryGroup)
        }
        result
    }

    // map of library groups keyed by group name
    val libraryGroupsByGroupId: Map<String, LibraryGroup> by lazy {
        val result = mutableMapOf<String, LibraryGroup>()
        for (association in libraryGroupAssociations) {
            // Check for duplicate groups
            val groupId = association.libraryGroup.group
            val existingAssociation = result.get(groupId)
            if (existingAssociation != null) {
                if (association.overrideIncludeInProjectPaths.size < 1) {
                    throw GradleException(
                        "Duplicate library group $groupId defined in " +
                            "${association.declarationName} does not set overrideInclude. " +
                            "Declarations beyond the first can only have an effect if they set " +
                            "overrideInclude")
                }
            } else {
                result.put(groupId, association.libraryGroup)
            }
        }
        result
    }

    // map from project name to group override if applicable
    val overrideLibraryGroupsByProjectPath: Map<String, LibraryGroup> by lazy {
        val result = mutableMapOf<String, LibraryGroup>()
        for (association in libraryGroupAssociations) {
            for (overridePath in association.overrideIncludeInProjectPaths) {
                result.put(overridePath, association.libraryGroup)
            }
        }
        result
    }

    private val libraryGroupAssociations: List<LibraryGroupAssociation> by lazy {
        val groups = getTable("groups")
        val useMultiplatformGroupVersion =
            parameters.useMultiplatformGroupVersions.orElse(false).get()

        fun readGroupVersion(groupDefinition: TomlTable, groupName: String, key: String): Version? {
            val versionRef = groupDefinition.getString(key) ?: return null
            if (!versionRef.startsWith(VersionReferencePrefix)) {
                throw GradleException(
                    "Group entry $key is expected to start with $VersionReferencePrefix"
                )
            }
            // name without `versions.`
            val atomicGroupVersionName = versionRef.removePrefix(
                VersionReferencePrefix
            )
            return libraryVersions[atomicGroupVersionName] ?: error(
                "Group entry $groupName specifies $atomicGroupVersionName, but such version " +
                    "doesn't exist"
            )
        }
        val result = mutableListOf<LibraryGroupAssociation>()
        // the toml library returns keySet unsorted, but libraryGroupsByGroupId requires it to be sorted
        for (name in groups.keySet().sorted()) {
            // get group name
            val groupDefinition = groups.getTable(name)!!
            val groupName = groupDefinition.getString("group")!!
            val finalGroupName = if (name.startsWith("COMPOSE") &&
                parameters.composeCustomGroup.isPresent
            ) {
                groupName.replace("androidx.compose", parameters.composeCustomGroup.get())
            } else {
                groupName
            }

            // get group version, if any
            val atomicGroupVersion = readGroupVersion(
                groupDefinition = groupDefinition,
                groupName = groupName,
                key = AtomicGroupVersion
            )
            val multiplatformGroupVersion = readGroupVersion(
                groupDefinition = groupDefinition,
                groupName = groupName,
                key = MultiplatformGroupVersion
            )
            check(
                multiplatformGroupVersion == null || atomicGroupVersion != null
            ) {
                "Cannot specify $MultiplatformGroupVersion for $name without specifying an " +
                    AtomicGroupVersion
            }
            val groupVersion = when {
                useMultiplatformGroupVersion -> multiplatformGroupVersion ?: atomicGroupVersion
                else -> atomicGroupVersion
            }

            val overrideApplyToProjects = (
                groupDefinition.getArray("overrideInclude")?.toList() ?: listOf()
                ).map({ it -> it as String })

            val group = LibraryGroup(finalGroupName, groupVersion)
            val association = LibraryGroupAssociation(name, group, overrideApplyToProjects)
            result.add(association)
        }
        result
    }
}

// a LibraryGroupSpec knows how to associate a LibraryGroup with the appropriate projects
data class LibraryGroupAssociation(
    // the name of the variable to which it is assigned in the toml file
    val declarationName: String,
    // the group
    val libraryGroup: LibraryGroup,
    // the paths of any additional projects that this group should be assigned to
    val overrideIncludeInProjectPaths: List<String>
)

private const val VersionReferencePrefix = "versions."
private const val AtomicGroupVersion = "atomicGroupVersion"
private const val MultiplatformGroupVersion = "multiplatformGroupVersion"
