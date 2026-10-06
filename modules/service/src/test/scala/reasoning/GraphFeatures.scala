package reasoning

import reasoning.steps.GraphFeatureSuite

// The features that need the graph: the service with Kafka, the sink and Neo4j beside it.

class PublicationFeatures extends GraphFeatureSuite("features/graph/publication.feature")

class BeliefChangeFeatures
    extends reasoning.steps.ExplainFeatureSuite("features/explain/belief-change.feature")
class CaseFeatures extends reasoning.steps.ExplainFeatureSuite("features/explain/case.feature")
class AsOfFeatures extends reasoning.steps.ExplainFeatureSuite("features/explain/as-of.feature")
class DisagreementFeatures
    extends reasoning.steps.ExplainFeatureSuite("features/explain/disagreement.feature")

class MarketsFeatures extends reasoning.steps.MarketFeatureSuite("features/market/markets.feature")
class ResolutionFeatures
    extends reasoning.steps.MarketFeatureSuite("features/market/resolution.feature")
class VocabularyFeatures
    extends reasoning.steps.MarketFeatureSuite("features/graph/vocabulary.feature")

class WithdrawalFeatures
    extends reasoning.steps.WithdrawalFeatureSuite("features/record/withdrawal.feature")
