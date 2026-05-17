package so.alaz.slate.display

import so.alaz.slate.probability.ProbabilityVariableService

object DisplayTextResolver {
    fun renderCrateTemplate(crateId: String, template: String): String =
        ProbabilityVariableService.render(template, ProbabilityVariableService.crateVariables(crateId))

    fun renderRewardTemplate(crateId: String, rewardId: String, template: String): String {
        val variables = ProbabilityVariableService.crateVariables(crateId) + ProbabilityVariableService.rewardVariables(crateId, rewardId)
        return ProbabilityVariableService.render(template, variables)
    }

    fun renderTierTemplate(crateId: String, tierId: String, template: String): String {
        val variables = ProbabilityVariableService.crateVariables(crateId) + ProbabilityVariableService.tierVariables(crateId, tierId)
        return ProbabilityVariableService.render(template, variables)
    }
}
