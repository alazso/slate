package so.alaz.slate.currency.service

object CurrencyValidation {
    private val idRegex = Regex("^[a-z][a-z0-9_]*$")
    private val providerRegex = Regex("^[a-z][a-z0-9_-]*$")

    fun isValidCurrencyId(value: String): Boolean = idRegex.matches(value)

    fun isValidProviderId(value: String): Boolean = providerRegex.matches(value)
}
