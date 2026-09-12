# Square Skies + Radiance DH Integration 0.1.1

Checkpoint containing only the verified unsigned DH coverage-wrap fix and version metadata over combined 0.1. Every cloud, water, lighting, payload, medium and diagnostic shader is otherwise byte-identical. Combined 0.1 ZIP is preserved unchanged.

The coverage helper now computes negative-coordinate wrapping with unsigned magnitude/remainder, including INT_MIN, instead of the old signed-remainder expression. This is the exact helper validated in the separate Water 0.4.1 coverage fix; no extra geometry, tint, height, memory-cap or distance-hide change was introduced. The build generator now copies a pinned local copy of that helper and asserts identity, preventing regeneration from restoring the old Water 0.4 helper.

Four affected DH any-hit stages compile and pass Vulkan 1.4 SPIR-V validation. The real native parser passes 73 attributes and 19 passes. Source comparison permits exactly two changed files: configs.json (display name only) and world/dh_coverage.glsl. All earlier combined 0.1 behavior and limitations remain, including Square's existing medium policy and the decision not to stack standalone Water 0.4 eye-path extinction onto it.

Runtime remains pending for this combined checkpoint. The user's initial DH5 + standalone Water 0.4.1 visual pass does not validate the combined pack. No live instance was written. For the next isolated test, use the matching DH5 native/Java pair and companion, select **Square Skies + Radiance DH Integration 0.1.1**, and keep all combined 0.1 defaults and diagnostic instructions. Frame generation is a separate native/mod integration component, not supplied by this ZIP.

The source-and-tests archive includes the current generator, pinned coverage source, packaging comparison assertions and prior combined contract checker. The original combined integration README is included separately for optics, controls, costs and provenance. Included GPL3/Apache2 notices remain unchanged. All new notes are UTF-8.
