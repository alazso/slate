package so.alaz.slate.key.providers

import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider

object PhysicalKeyProviderRegistry {
    private val providers = mapOf(
        KeyProvider.Vanilla to VanillaPhysicalKeyProvider,
        KeyProvider.Nexo to NexoPhysicalKeyProvider,
        KeyProvider.CraftEngine to CraftEnginePhysicalKeyProvider,
        KeyProvider.ItemsAdder to ItemsAdderPhysicalKeyProvider,
    )

    fun providerFor(crate: CrateDefinition): PhysicalKeyProvider =
        providers.getValue(crate.keyDefinition.provider)

    fun validate(crate: CrateDefinition): String? {
        val provider = providerFor(crate)
        if (!provider.isAvailable()) {
            return "Key provider '${crate.keyDefinition.provider}' is not available for crate '${crate.id}'"
        }
        if (crate.keyDefinition.provider != KeyProvider.Vanilla) {
            val providerItemId = crate.keyDefinition.providerItemId?.trim().orEmpty()
            if (providerItemId.isBlank()) {
                return "key.providerItemId is required when key.provider is '${crate.keyDefinition.provider}' for crate '${crate.id}'"
            }
            if (provider.mint(crate, 1) == null) {
                return "Provider '${crate.keyDefinition.provider}' could not resolve key.providerItemId='$providerItemId' for crate '${crate.id}'"
            }
        }
        return null
    }
}
