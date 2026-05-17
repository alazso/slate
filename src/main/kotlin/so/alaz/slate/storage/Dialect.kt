package so.alaz.slate.storage

sealed class Dialect {
    data object Sqlite : Dialect()
    data object Postgres : Dialect()
}
