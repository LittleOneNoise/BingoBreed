package fr.lilone.bingobreed.sniffer.parser

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FileDescriptor
import org.slf4j.LoggerFactory

/**
 * Charge le `FileDescriptorSet` produit depuis `output.proto` (protodec) et indexe
 * tous les messages par nom.
 *
 * Comme les noms de messages d'output.proto = les codes des `type_url`
 * (`type.ankama.com/<code>`), on résout n'importe quel message de jeu pour le
 * décoder en `DynamicMessage` — sans code typé, donc robuste aux shuffles : il
 * suffit de régénérer le descripteur via protodec à chaque patch.
 */
class DescriptorRegistry private constructor(
    private val byFullName: Map<String, Descriptor>,
    private val byShortName: Map<String, Descriptor>,
) {
    val size: Int get() = byFullName.size

    /** Résout le descripteur d'un `type_url` (`type.ankama.com/jtg` → message `jtg`). */
    fun findByTypeUrl(typeUrl: String): Descriptor? {
        val name = typeUrl.substringAfterLast('/')
        return byFullName[name] ?: byShortName[name.substringAfterLast('.')]
    }

    companion object {
        private val log = LoggerFactory.getLogger(DescriptorRegistry::class.java)
        const val DEFAULT_RESOURCE = "dofus-raw.desc"

        fun loadFromClasspath(resource: String = DEFAULT_RESOURCE): DescriptorRegistry {
            val stream = DescriptorRegistry::class.java.classLoader.getResourceAsStream(resource)
            if (stream == null) {
                log.warn("Descripteur '{}' absent du classpath — décodage DynamicMessage désactivé", resource)
                return DescriptorRegistry(emptyMap(), emptyMap())
            }
            return stream.use { load(FileDescriptorSet.parseFrom(it)) }
        }

        fun load(set: FileDescriptorSet): DescriptorRegistry {
            val full = HashMap<String, Descriptor>()
            val short = HashMap<String, Descriptor>()
            buildFileDescriptors(set).forEach { fd -> index(fd.messageTypes, full, short) }
            log.info("Descripteurs chargés: {} message(s)", full.size)
            return DescriptorRegistry(full, short)
        }

        private fun index(
            messages: List<Descriptor>,
            full: MutableMap<String, Descriptor>,
            short: MutableMap<String, Descriptor>,
        ) {
            for (m in messages) {
                full[m.fullName] = m
                short.putIfAbsent(m.name, m) // collision improbable pour les codes top-level
                index(m.nestedTypes, full, short)
            }
        }

        /** Construit les FileDescriptor en résolvant les dépendances (imports inclus). */
        private fun buildFileDescriptors(set: FileDescriptorSet): List<FileDescriptor> {
            val protoByName = set.fileList.associateBy { it.name }
            val built = LinkedHashMap<String, FileDescriptor>()
            fun build(name: String): FileDescriptor {
                built[name]?.let { return it }
                val proto = protoByName[name] ?: error("Descripteur de fichier manquant: $name")
                val deps = proto.dependencyList.map { build(it) }.toTypedArray()
                return FileDescriptor.buildFrom(proto, deps).also { built[name] = it }
            }
            set.fileList.forEach { build(it.name) }
            return built.values.toList()
        }
    }
}
