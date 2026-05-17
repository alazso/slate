package so.alaz.slate.key

sealed interface KeyType {
    data object Physical : KeyType
    data object Virtual : KeyType
}
