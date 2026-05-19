package so.alaz.slate.gui.editor

/**
 * Backwards-compatible entry point used by `/sla crate editor`.
 *
 * Inherits everything from the spec-compliant `CrateManagerGui` so the legacy command surface keeps
 * working without re-implementing the manager.
 */
class CrateListGui : CrateManagerGui()
