/*
 * Copyright 2018 The Android Open Source Project
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

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.utils.childrenIterator
import com.android.utils.forEach
import com.android.utils.mapValuesNotNull
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.StringTokenizer
import org.apache.xerces.jaxp.SAXParserImpl.JAXPSAXParser
import org.dom4j.Document
import org.dom4j.DocumentException
import org.dom4j.DocumentFactory
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import androidx.build.jetbrains.ArtifactRedirecting
import androidx.build.jetbrains.artifactRedirecting
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

fun Project.configureMavenArtifactUpload(
    extension: AndroidXExtension,
    kmpExtension: AndroidXMultiplatformExtension,
    componentFactory: SoftwareComponentFactory
) {
    apply(mapOf("plugin" to "maven-publish"))
    var registered = false
    fun registerOnFirstPublishableArtifact(component: SoftwareComponent) {
        if (!registered) {
            configureComponentPublishing(extension, kmpExtension, component, componentFactory)
            Release.register(this, extension)
            registered = true
        }
    }
    afterEvaluate {
        if (!extension.shouldPublish()) {
            return@afterEvaluate
        }
        components.all { component ->
            if (isValidReleaseComponent(component)) {
                registerOnFirstPublishableArtifact(component)
            }
        }
    }
    // validate that all libraries that should be published actually get registered.
    gradle.taskGraph.whenReady {
        if (releaseTaskShouldBeRegistered(extension)) {
            tasks.findByName(Release.PROJECT_ARCHIVE_ZIP_TASK_NAME)
                ?: throw GradleException("Project $name is configured for publishing, but a " +
                    "'createProjectZip' task was never registered. This is likely a bug in" +
                    "AndroidX plugin configuration")
        }
    }
}

private fun Project.releaseTaskShouldBeRegistered(extension: AndroidXExtension): Boolean {
    if (plugins.hasPlugin(AppPlugin::class.java)) { return false }
    if (!extension.shouldRelease() && !isSnapshotBuild()) { return false }
    return extension.shouldPublish()
}

/**
 * Configure publishing for a [SoftwareComponent].
 */
private fun Project.configureComponentPublishing(
    extension: AndroidXExtension,
    kmpExtension: AndroidXMultiplatformExtension,
    component: SoftwareComponent,
    componentFactory: SoftwareComponentFactory
) {
    val androidxGroup = validateCoordinatesAndGetGroup(extension)
    val projectArchiveDir = File(
        getRepositoryDirectory(),
        "${androidxGroup.group.replace('.', '/')}/$name"
    )
    group = androidxGroup.group

    val mavenCoordsToRedirecting: Provider<Map<String, ArtifactRedirecting>> = provider {
        project
            .getProjectsMap()
            .values
            .mapNotNull { project.findProject(it) }
            .associateBy {
                val group = it.group
                val name = it.name
                "$group:$name"
            }
            .mapValuesNotNull { it.value.artifactRedirecting() }
    }

    /*
     * Provides a set of maven coordinates (groupId:artifactId) of artifacts in AndroidX
     * that are Android Libraries.
     */
    val androidLibrariesSetProvider: Provider<Set<String>> = provider {
        val androidxAndroidProjects = mutableSetOf<String>()
        // Check every project is the project map to see if they are an Android Library
        val projectModules = project.getProjectsMap()
        for ((mavenCoordinates, projectPath) in projectModules) {
            project.findProject(projectPath)?.plugins?.let { plugins ->
                if (plugins.hasPlugin(LibraryPlugin::class.java)) {
                    if (plugins.hasPlugin(KotlinMultiplatformPluginWrapper::class.java)) {
                        // For KMP projects, android AAR is published under -android
                        androidxAndroidProjects.add("$mavenCoordinates-android")
                    } else {
                        androidxAndroidProjects.add(mavenCoordinates)
                    }
                }
            }
        }
        androidxAndroidProjects
    }

    configure<PublishingExtension> {
        repositories {
            it.maven { repo ->
                repo.setUrl(getRepositoryDirectory())
            }
        }
        publications {
            if (appliesJavaGradlePluginPlugin()) {
                // The 'java-gradle-plugin' will also add to the 'pluginMaven' publication
                it.create<MavenPublication>("pluginMaven")
                tasks.getByName("publishPluginMavenPublicationToMavenRepository").doFirst {
                    removePreviouslyUploadedArchives(projectArchiveDir)
                }
            } else {
                if (project.isMultiplatformPublicationEnabled()) {
                    configureMultiplatformPublication(componentFactory)
                } else {
                    it.create<MavenPublication>("maven") {
                        from(component)
                    }
                    tasks.getByName("publishMavenPublicationToMavenRepository").doFirst {
                        removePreviouslyUploadedArchives(projectArchiveDir)
                    }
                }
            }
        }
        publications.withType(MavenPublication::class.java).all { publication ->
            publication.pom { pom ->
                addInformativeMetadata(extension, pom)
                tweakDependenciesMetadata(
                    pom, androidLibrariesSetProvider,
                    publication.name == KMP_ANCHOR_PUBLICATION_NAME, kmpExtension.defaultPlatform)
            }
        }
    }

    // Workarounds for https://github.com/gradle/gradle/issues/20011
    project.tasks.withType(GenerateModuleMetadata::class.java).configureEach { task ->
        task.doLast {
            val metadataFile = task.outputFile.asFile.get()
            val metadata = metadataFile.readText()
            val sortedMetadata = sortGradleMetadataDependencies(metadata)

            if (metadata != sortedMetadata) {
                metadataFile.writeText(sortedMetadata)
            }
        }
    }
    project.tasks.withType(GenerateMavenPom::class.java).configureEach { task ->
        task.doLast {
            val pomFile = task.destination
            val pom = pomFile.readText()
            val modifiedPom = modifyPomDependencies(pom, mavenCoordsToRedirecting.get())

            if (pom != modifiedPom) {
                pomFile.writeText(modifiedPom)
            }
        }
    }

    // Workaround for https://github.com/gradle/gradle/issues/11717
    project.tasks.withType(GenerateModuleMetadata::class.java).configureEach { task ->
        task.doLast {
            val metadata = task.outputFile.asFile.get()
            val text = metadata.readText()
            metadata.writeText(
                text.replace(
                    "\"buildId\": .*".toRegex(),
                    "\"buildId:\": \"${getBuildId()}\""
                )
            )
        }
    }
}

/**
 * Looks for a dependencies XML element within [pom], sorts its contents and modify it by redirecting coordinates
 */
internal fun modifyPomDependencies(
    pom: String,
    mavenCoordsToRedirecting: Map<String, ArtifactRedirecting>
): String {
    // Workaround for using the default namespace in dom4j.
    val namespaceUris = mapOf("ns" to "http://maven.apache.org/POM/4.0.0")
    val docFactory = DocumentFactory()
    docFactory.xPathNamespaceURIs = namespaceUris
    // Ensure that we're consistently using JAXP parser.
    val xmlReader = JAXPSAXParser()
    val document = parseText(docFactory, xmlReader, pom)

    // For each <dependencies> element, sort the contained elements in-place.
    document.rootElement
        .selectNodes("ns:dependencies")
        .filterIsInstance<Element>()
        .forEach { element ->
            val deps = element.elements()
            val modifiedDeps = deps
                .map { modifyPomDependency(it, mavenCoordsToRedirecting) }
                .sortedBy { it.stringValue }

            // Content contains formatting nodes, so to avoid modifying those we replace
            // each element with the sorted element from its respective index. Note this
            // will not move adjacent elements, so any comments would remain in their
            // original order.
            element.content().replaceAll {
                val index = deps.indexOf(it)
                if (index >= 0) {
                    modifiedDeps[index]
                } else {
                    it
                }
            }
        }

    // Write to string. Note that this does not preserve the original indent level, but it
    // does preserve line breaks -- not that any of this matters for client XML parsing.
    val stringWriter = StringWriter()
    XMLWriter(stringWriter).apply {
        setIndentLevel(2)
        write(document)
        close()
    }

    return stringWriter.toString()
}

internal fun modifyPomDependency(
    dependency: Element,
    mavenCoordsToRedirecting: Map<String, ArtifactRedirecting>
): Element {
    val groupId = dependency.selectSingleNode("ns:groupId")
    val artifactId = dependency.selectSingleNode("ns:artifactId")
    val version = dependency.selectSingleNode("ns:version")
    val name = artifactId.stringValue.substringBeforeLast("-")
    val target = artifactId.stringValue.substringAfterLast("-")

    val coords = "${groupId.stringValue}:$name"
    val redirecting = mavenCoordsToRedirecting[coords] ?: return dependency
    val shouldReplace = redirecting.targetNames.contains(target)
    if (shouldReplace) {
        groupId.text = redirecting.groupId
        version.text = redirecting.versionForTargetOrDefault(target)
    }
    return dependency
}

// Coped from org.dom4j.DocumentHelper with modifications to allow SAXReader configuration.
@Throws(DocumentException::class)
fun parseText(
    documentFactory: DocumentFactory,
    xmlReader: XMLReader,
    text: String,
): Document {
    val reader = SAXReader.createDefault()
    reader.documentFactory = documentFactory
    reader.xmlReader = xmlReader
    val encoding = getEncoding(text)
    val source = InputSource(StringReader(text))
    source.encoding = encoding
    val result = reader.read(source)
    if (result.xmlEncoding == null) {
        result.xmlEncoding = encoding
    }
    return result
}

// Coped from org.dom4j.DocumentHelper.
private fun getEncoding(text: String): String? {
    var result: String? = null
    val xml = text.trim { it <= ' ' }
    if (xml.startsWith("<?xml")) {
        val end = xml.indexOf("?>")
        val sub = xml.substring(0, end)
        val tokens = StringTokenizer(sub, " =\"'")
        while (tokens.hasMoreTokens()) {
            val token = tokens.nextToken()
            if ("encoding" == token) {
                if (tokens.hasMoreTokens()) {
                    result = tokens.nextToken()
                }
                break
            }
        }
    }
    return result
}

/**
 * Looks for a dependencies JSON element within [metadata] and sorts its contents.
 */
fun sortGradleMetadataDependencies(metadata: String): String {
    val gson = GsonBuilder().create()
    val jsonObj = gson.fromJson(metadata, JsonObject::class.java)!!
    jsonObj.getAsJsonArray("variants").forEach { entry ->
        (entry as? JsonObject)?.getAsJsonArray("dependencies")?.let { jsonArray ->
            val sortedSet = jsonArray.toSortedSet(compareBy { it.toString() })
            jsonArray.removeAll { true }
            sortedSet.forEach { element -> jsonArray.add(element) }
        }
    }

    val stringWriter = StringWriter()
    val jsonWriter = JsonWriter(stringWriter)
    jsonWriter.setIndent("  ")
    gson.toJson(jsonObj, jsonWriter)
    return stringWriter.toString()
}

private fun Project.isMultiplatformPublicationEnabled(): Boolean {
    return extensions.findByType<KotlinMultiplatformExtension>() != null
}

private val jetBrainsLibrariesWithAndroidTarget = setOf(
    ":compose:ui:ui-backhandler",
)
private fun Project.configureMultiplatformPublication(componentFactory: SoftwareComponentFactory) {
    if (project.path !in jetBrainsLibrariesWithAndroidTarget) return
    val multiplatformExtension = extensions.findByType<KotlinMultiplatformExtension>()!!
    multiplatformExtension.targets.all { target ->
        if (target is KotlinAndroidTarget) {
            target.publishLibraryVariants(
                Release.DEFAULT_PUBLISH_CONFIG,
                "debug"
            )
        }
    }

    replaceBaseMultiplatformPublication(componentFactory)
}

/**
 * KMP does not include a sources configuration (b/235486368), so we replace it with our own
 * publication that includes it.  This uses internal API as a workaround while waiting for a fix
 * on the original bug.
 */
private fun Project.replaceBaseMultiplatformPublication(
    componentFactory: SoftwareComponentFactory
) {
    val kotlinComponent = components.findByName("kotlin") as SoftwareComponentInternal
    withSourcesComponents(
        componentFactory,
        setOf("androidxSourcesElements")
    ) { sourcesComponents ->
        configure<PublishingExtension> {
            publications { pubs ->
                pubs.create<MavenPublication>(KMP_ANCHOR_PUBLICATION_NAME) {
                    // Duplicate behavior from KMP plugin
                    // (https://cs.github.com/JetBrains/kotlin/blob/0c001cc9939a2ab11815263ed825c1096b3ce087/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/Publishing.kt#L42)
                    // Should be able to remove internal API usage once
                    // https://youtrack.jetbrains.com/issue/KT-36943 is fixed
                    (this as MavenPublicationInternal).publishWithOriginalFileName()

                    from(object : ComponentWithVariants, SoftwareComponentInternal {
                        override fun getName(): String {
                            return KMP_ANCHOR_PUBLICATION_NAME
                        }

                        override fun getUsages(): MutableSet<out UsageContext> {
                            // Include sources artifact we built and root artifacts from kotlin plugin.
                            return (
                                sourcesComponents.flatMap { it.usages } +
                                kotlinComponent.usages
                            ).toMutableSet()
                        }

                        override fun getVariants(): MutableSet<out SoftwareComponent> {
                            // Include all target-based variants from kotlin plugin.
                            return (kotlinComponent as ComponentWithVariants).variants
                        }
                    })
                }

                // mark original publication as an alias, so we do not try to publish it.
                pubs.named("kotlinMultiplatform").configure {
                    it as MavenPublicationInternal
                    it.isAlias = true
                }
            }

            disableBaseKmpPublications()
        }
    }
}

/**
 * If source configurations with the given names are currently in the project, or if they
 * eventually gets added, run the given [action] with those configurations as software components.
 */
private fun Project.withSourcesComponents(
    componentFactory: SoftwareComponentFactory,
    names: Set<String>,
    action: (List<SoftwareComponentInternal>) -> Unit
) {
    val targetConfigurations = mutableSetOf<Configuration>()
    configurations.configureEach {
        if (it.name in names) {
            targetConfigurations.add(it)
            if (targetConfigurations.size == names.size) {
                action(
                    targetConfigurations.map { configuration ->
                        componentFactory.adhoc(configuration.name).apply {
                            addVariantsFromConfiguration(configuration) {}
                        } as SoftwareComponentInternal
                    }
                )
            }
        }
    }
}

/**
 * Now that we have created our own publication that we want published, prevent the base publication
 * from being published using the roll-up tasks.  We should be able to remove this workaround when
 * b/235486368 is fixed.
 */
private fun Project.disableBaseKmpPublications() {
    listOf("publish", "publishToMavenLocal").forEach { taskName ->
        tasks.named(taskName).configure { publishTask ->
            publishTask.setDependsOn(publishTask.dependsOn.filterNot {
                (it as String).startsWith("publishKotlinMultiplatform")
            })
        }
    }
}

private fun Project.isValidReleaseComponent(component: SoftwareComponent) =
    component.name == releaseComponentName()

private fun Project.releaseComponentName() = when {
    plugins.hasPlugin(KotlinMultiplatformPluginWrapper::class.java) -> "kotlin"
    plugins.hasPlugin(JavaPlugin::class.java) -> "java"
    else -> "release"
}

private fun Project.validateCoordinatesAndGetGroup(extension: AndroidXExtension): LibraryGroup {
    val mavenGroup = extension.mavenGroup
    if (mavenGroup == null) {
        val groupExplanation = extension.explainMavenGroup().joinToString("\n")
        throw Exception("You must specify mavenGroup for $path :\n$groupExplanation")
    }
    val strippedGroupId = mavenGroup.group.substringAfterLast(".")
    if (
        !extension.bypassCoordinateValidation &&
        mavenGroup.group.startsWith("androidx") &&
        !name.startsWith(strippedGroupId)
    ) {
        throw Exception("Your artifactId must start with '$strippedGroupId'. (currently is $name)")
    }
    return mavenGroup
}

/**
 * Delete any existing archives, so that developers don't get
 * confused/surprised by the presence of old versions.
 * Additionally, deleting old versions makes it more convenient to iterate
 * over all existing archives without visiting archives having old versions too
 */
private fun removePreviouslyUploadedArchives(projectArchiveDir: File) {
    projectArchiveDir.deleteRecursively()
}

private fun Project.addInformativeMetadata(extension: AndroidXExtension, pom: MavenPom) {
    pom.name.set(extension.name)
    pom.description.set(provider { extension.description })
    pom.url.set(
        provider {
            fun defaultUrl() = "https://developer.android.com/jetpack/androidx/releases/" +
                extension.mavenGroup!!.group.removePrefix("androidx.")
                    .replace(".", "-") +
                "#" + extension.project.version()
            getAlternativeProjectUrl() ?: defaultUrl()
        }
    )
    pom.inceptionYear.set(provider { extension.inceptionYear })
    pom.licenses { licenses ->
        licenses.license { license ->
            license.name.set("The Apache Software License, Version 2.0")
            license.url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            license.distribution.set("repo")
        }
        for (extraLicense in extension.getLicenses()) {
            licenses.license { license ->
                license.name.set(provider { extraLicense.name })
                license.url.set(provider { extraLicense.url })
                license.distribution.set("repo")
            }
        }
    }
    pom.scm { scm ->
        scm.url.set("https://cs.android.com/androidx/platform/frameworks/support")
        scm.connection.set(ANDROID_GIT_URL)
    }
    pom.developers { devs ->
        devs.developer { dev ->
            dev.name.set("The Android Open Source Project")
        }
    }
}

private fun tweakDependenciesMetadata(
    pom: MavenPom,
    androidLibrariesSetProvider: Provider<Set<String>>,
    kmpAnchor: Boolean,
    pomPlatform: String?
) {
    pom.withXml { xml ->
        // The following code depends on getProjectsMap which is only available late in
        // configuration at which point Java Library plugin's variants are not allowed to be
        // modified. TODO remove the use of getProjectsMap and move to earlier configuration.
        // For more context see:
        // https://android-review.googlesource.com/c/platform/frameworks/support/+/1144664/8/buildSrc/src/main/kotlin/androidx/build/MavenUploadHelper.kt#177
        assignAarTypes(xml, androidLibrariesSetProvider.get())
        ensureConsistentJvmSuffix(xml)

        if (kmpAnchor && pomPlatform != null) {
            insertDefaultMultiplatformDependencies(xml, pomPlatform)
        }
    }
}

// TODO(aurimas): remove this when Gradle bug is fixed.
// https://github.com/gradle/gradle/issues/3170
fun assignAarTypes(
    xml: XmlProvider,
    androidLibrariesSet: Set<String>
) {
    val xmlElement = xml.asElement()
    val dependencies = xmlElement.find {
        it.nodeName == "dependencies"
    } as? org.w3c.dom.Element

    dependencies?.getElementsByTagName("dependency")?.forEach { dependency ->
        val groupId = dependency.find { it.nodeName == "groupId" }?.textContent
            ?: throw IllegalArgumentException("Failed to locate groupId node")
        val artifactId = dependency.find { it.nodeName == "artifactId" }?.textContent
            ?: throw IllegalArgumentException("Failed to locate artifactId node")
        if (androidLibrariesSet.contains("$groupId:$artifactId")) {
            dependency.appendElement("type", "aar")
        }
    }
}

fun insertDefaultMultiplatformDependencies(
    xml: XmlProvider,
    platformId: String
) {
    val xmlElement = xml.asElement()
    val groupId = xmlElement.find { it.nodeName == "groupId" }?.textContent
        ?: throw IllegalArgumentException("Failed to locate groupId node")
    val artifactId = xmlElement.find { it.nodeName == "artifactId" }?.textContent
        ?: throw IllegalArgumentException("Failed to locate artifactId node")
    val version = xmlElement.find { it.nodeName == "version" }?.textContent
        ?: throw IllegalArgumentException("Failed to locate version node")

    // Find the top-level <dependencies> element or add one if there are no other dependencies.
    val dependencies = xmlElement.find {
        it.nodeName == "dependencies"
    } ?: xmlElement.appendElement("dependencies")
    dependencies.appendElement("dependency").apply {
        appendElement("groupId", groupId)
        appendElement("artifactId", "$artifactId-$platformId")
        appendElement("version", version)
        appendElement("scope", "runtime")
    }
}

private fun org.w3c.dom.Node.appendElement(
    tagName: String,
    textValue: String? = null
): org.w3c.dom.Element {
    val element = ownerDocument.createElement(tagName)
    appendChild(element)

    if (textValue != null) {
        val textNode = ownerDocument.createTextNode(textValue)
        element.appendChild(textNode)
    }

    return element
}

private fun org.w3c.dom.Node.find(
    predicate: (org.w3c.dom.Node) -> Boolean
): org.w3c.dom.Node? {
    val iterator = childrenIterator()
    while (iterator.hasNext()) {
        val node = iterator.next()
        if (predicate(node)) {
            return node
        }
    }
    return null
}

/**
 * Ensures that artifactIds are consistent when using configuration caching.
 * A workaround for https://github.com/gradle/gradle/issues/18369
 */
fun ensureConsistentJvmSuffix(
    xml: XmlProvider
) {
    val dependencies = xml.asElement().find {
        it.nodeName == "dependencies"
    } as? org.w3c.dom.Element ?: return

    dependencies.getElementsByTagName("dependency").forEach { dependency ->
        val artifactId = dependency.find { it.nodeName == "artifactId" }
            ?: throw IllegalArgumentException("Failed to locate artifactId node")
        // kotlinx-coroutines-core is only a .pom and only depends on kotlinx-coroutines-core-jvm,
        // so the two artifacts should be approximately equivalent. However,
        // when loading from configuration cache, Gradle often returns a different resolution.
        // We replace it here to ensure consistency and predictability, and
        // to avoid having to rerun any zip tasks that include it
        if (artifactId.textContent == "kotlinx-coroutines-core-jvm") {
            artifactId.textContent = "kotlinx-coroutines-core"
        }
    }
}

private fun Project.appliesJavaGradlePluginPlugin() = pluginManager.hasPlugin("java-gradle-plugin")

private const val ANDROID_GIT_URL =
    "scm:git:https://android.googlesource.com/platform/frameworks/support"

internal const val KMP_ANCHOR_PUBLICATION_NAME = "androidxKmp"
