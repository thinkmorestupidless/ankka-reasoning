package reasoning

import reasoning.steps.ProcessFeatureSuite

/** The process layer's living features: with the service alone, and with the graph beside it. */
class ToolsFeatures extends ProcessFeatureSuite("features/process/tools.feature")
class RunsFeatures  extends ProcessFeatureSuite("features/process/runs.feature", graph = true)
