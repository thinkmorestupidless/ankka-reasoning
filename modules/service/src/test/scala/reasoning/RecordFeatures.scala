package reasoning

import reasoning.steps.FeatureSuite

// One suite per feature file, so a suite never meets a step that is not yet bound. These five run
// the whole service with an in-memory broker and no graph database.

class QuestionsFeatures         extends FeatureSuite("features/record/questions.feature")
class HoldersAndSourcesFeatures extends FeatureSuite("features/record/holders-and-sources.feature")
class EvidenceFeatures          extends FeatureSuite("features/record/evidence.feature")
class ClaimsFeatures            extends FeatureSuite("features/record/claims.feature")
class BeliefsFeatures           extends FeatureSuite("features/record/beliefs.feature")
